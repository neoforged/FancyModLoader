/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.zip.ZipFile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.internal.binarypatchapplier.PatchBase;
import net.neoforged.internal.binarypatchapplier.Patcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the NeoForge patches to the original Minecraft jar.
 * This is used in production only, because in userdev, the jar is created by the Gradle tooling, while in neodev, the
 * Minecraft classes are in folders on the classpath.
 */
class MinecraftPatcher {
    private static final Logger LOG = LoggerFactory.getLogger(MinecraftPatcher.class);

    static void discoverOrInstall(ClassLoader classLoader, Dist requiredDist, NeoForgeInfo neoForgeInfo, Path destinationPath) {
        var progress = StartupNotificationManager.addProgressBar("Patching Minecraft", 3);
        progress.label("Patching - Looking for original game jar");
        var minecraftJar = findOriginalMinecraftOnClasspath(classLoader, requiredDist);

        // Create a temporary file for patching in the destination directory
        Path destinationDir = Objects.requireNonNullElse(destinationPath.getParent(), Path.of("./"));
        try {
            Files.createDirectories(destinationDir);
        } catch (IOException e) {
            LOG.error("Failed to create patched game jar destination directory {}", destinationDir, e);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.patching_failed").withCause(e));
        }

        Path tempDestinationPath;
        try {
            tempDestinationPath = Files.createTempFile(destinationDir, destinationPath.getFileName().toString(), ".tmp");
        } catch (IOException e) {
            LOG.error("Failed to create temporary game jar file in {}", destinationDir, e);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.patching_failed").withCause(e));
        }

        try {
            try (var patchesStream = openPatches(neoForgeInfo)) {
                progress.increment();
                progress.label("Patching - Applying NeoForge patches to game");

                Patcher.patch(
                        minecraftJar.toFile(),
                        requiredDist == Dist.CLIENT ? PatchBase.CLIENT : PatchBase.SERVER,
                        neoForgeInfo.sourcePath() + "!" + neoForgeInfo.patchBundleLocation(),
                        patchesStream,
                        tempDestinationPath.toFile(),
                        (message) -> LOG.debug("Patching output: {}", message));
            } catch (Exception e) {
                LOG.error("Failed to apply patches from '{}' to Minecraft.", neoForgeInfo.sourcePath(), e);
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.patching_failed").withCause(e));
            }

            progress.increment();
            progress.label("Patching - Saving patched jar");

            moveAtomically(tempDestinationPath, destinationPath);
        } finally {
            try {
                Files.deleteIfExists(tempDestinationPath);
            } catch (IOException e) {
                LOG.error("Failed to delete the temporary game jar file {}", tempDestinationPath, e);
            }
        }

        progress.increment();
        progress.complete();
    }

    private static ByteArrayInputStream openPatches(NeoForgeInfo neoForgeInfo) {
        // Since this is only used in Production, we assume that the source path is a file
        ZipFile zipFile;
        try {
            zipFile = new ZipFile(neoForgeInfo.sourcePath().toFile());
        } catch (IOException e) {
            LOG.error("Failed to open the NeoForge jar {}", neoForgeInfo.sourcePath(), e);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar"));
        }
        try (zipFile) {
            var entry = zipFile.getEntry(neoForgeInfo.patchBundleLocation());
            if (entry == null) {
                LOG.error("The NeoForge jar at '{}' does not contain the patches at '{}'", neoForgeInfo.sourcePath(), neoForgeInfo.patchBundleLocation());
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar"));
            }
            try (var input = zipFile.getInputStream(entry)) {
                return new ByteArrayInputStream(input.readAllBytes());
            }
        } catch (IOException e) {
            LOG.error("Fail to read the patches from '{}' within the NeoForge jar at '{}'", neoForgeInfo.patchBundleLocation(), neoForgeInfo.sourcePath());
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar"));
        }
    }

    /**
     * Searches the classpath for specific Minecraft classes and uses their containing Jar file as
     * the input for patching.
     */
    private static Path findOriginalMinecraftOnClasspath(ClassLoader classLoader, Dist requiredDist) {
        String entrypoint = switch (requiredDist) {
            case CLIENT -> "net/minecraft/client/main/Main.class";
            // TODO: This could false-positive detect a client jar as the server jar
            case DEDICATED_SERVER -> "net/minecraft/server/Main.class";
        };

        var jarsWithEntrypoint = new HashSet<Path>();
        try {
            var resources = classLoader.getResources(entrypoint);
            while (resources.hasMoreElements()) {
                jarsWithEntrypoint.add(ClasspathResourceUtils.findJarPathFor(entrypoint, "minecraft jar", resources.nextElement()));
            }
        } catch (IOException e) {
            LOG.error("Unexpected I/O error during search for Minecraft jar.", e);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withCause(e));
        }

        if (jarsWithEntrypoint.isEmpty()) {
            LOG.error("Failed to find the original Minecraft jar on the classpath");
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.missing_original_minecraft_jar"));
        } else if (jarsWithEntrypoint.size() > 1) {
            LOG.error("Found multiple copies of Minecraft on the classpath: {}", jarsWithEntrypoint);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
        }

        var gameJar = jarsWithEntrypoint.iterator().next();
        LOG.info("Found original game jar: '{}'", gameJar);
        return gameJar;
    }

    /**
     * Atomically moves the extracted embedded jar file to its final location.
     * If an atomic move is not supported, the file will be moved normally.
     */
    private static void moveAtomically(Path source, Path destination) {
        try {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Failed to move installation {} to its final location {}", source, destination, e);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.patching_failed").withAffectedPath(source).withCause(e));
        }
    }
}
