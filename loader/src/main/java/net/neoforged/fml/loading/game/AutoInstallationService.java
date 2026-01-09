/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Properties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.internal.binarypatchapplier.PatchBase;
import net.neoforged.internal.binarypatchapplier.Patcher;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AutoInstallationService {
    private static final Logger LOG = LoggerFactory.getLogger(AutoInstallationService.class);
    private static final String AUTOINSTALL_PATCH_LOCATION = "autoinstall_patches";

    public static void uninstall(Dist distToUninstall, String neoForgeVersion) throws IOException {
        var patchedMinecraftPath = FMLPaths.AUTOINSTALL_CACHEDIR.get().resolve((switch (distToUninstall) {
            case CLIENT -> new MavenCoordinate("net.neoforged", "minecraft-client-patched", "", "", neoForgeVersion);
            case DEDICATED_SERVER -> new MavenCoordinate("net.neoforged", "minecraft-server-patched", "", "", neoForgeVersion);
        }).toRelativeRepositoryPath());

        Files.deleteIfExists(patchedMinecraftPath);
    }

    @Nullable
    static Path discoverOrInstall(Dist requiredDist, String neoForgeVersion, ClassLoader classLoader) throws IOException {
        if (!requiredDist.isClient())
            return null;

        var progress = StartupNotificationManager.addProgressBar("Installation", 3);
        progress.label("Installation - Preparing resources...");

        var tempDir = Files.createTempDirectory("fml-auto-installer");
        var patchesStream = extractPatchesStream(classLoader);
        if (patchesStream == null) {
            LOG.warn("Failed to find patches. Could not perform auto installation!");
            progress.complete();
            return null;
        }

        var minecraftJar = getRawMinecraftClientJar();
        var output = tempDir.resolve("client.jar");

        progress.increment();
        progress.label("Installation - Installing NeoForge...");

        Patcher.patch(
                minecraftJar.toFile(),
                PatchBase.CLIENT,
                "patches",
                patchesStream,
                output.toFile(),
                (ignored) -> {} //We are not outputting debugging information at the moment.
        );

        progress.increment();
        progress.label("Installation - Finalizing changes...");

        var patchedMinecraftPath = copyToCacheDirectory(neoForgeVersion, requiredDist, output);

        progress.increment();
        progress.complete();

        return patchedMinecraftPath;
    }

    private static Path copyToCacheDirectory(final String neoForgeVersion, final Dist dist, final Path output) throws IOException {
        var patchedMinecraftPath = FMLPaths.AUTOINSTALL_CACHEDIR.get().resolve((switch (dist) {
            case CLIENT -> new MavenCoordinate("net.neoforged", "minecraft-client-patched", "", "", neoForgeVersion);
            case DEDICATED_SERVER -> new MavenCoordinate("net.neoforged", "minecraft-server-patched", "", "", neoForgeVersion);
        }).toRelativeRepositoryPath());

        moveInstallationIntoTarget(output, patchedMinecraftPath);
        return patchedMinecraftPath;
    }

    @Nullable
    private static InputStream extractPatchesStream(ClassLoader classLoader) {
        try (var in = classLoader.getResourceAsStream("net/neoforged/neoforge/common/version.properties")) {
            if (in == null) {
                return null;
            }

            Properties properties = new Properties();
            properties.load(new BufferedInputStream(in));

            if (!properties.containsKey(AUTOINSTALL_PATCH_LOCATION))
                return null;

            var patchesInnerPath = properties.get(AUTOINSTALL_PATCH_LOCATION).toString();
            return classLoader.getResourceAsStream(patchesInnerPath);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Path getRawMinecraftClientJar() throws IOException {
        var jarsWithEntrypoint = new HashSet<Path>();

        var ourCl = Thread.currentThread().getContextClassLoader();
        var resources = ourCl.getResources("net/minecraft/client/main/Main.class");
        while (resources.hasMoreElements()) {
            jarsWithEntrypoint.add(ClasspathResourceUtils.findJarPathFor("net/minecraft/client/main/Main.class", "minecraft jar", resources.nextElement()));
        }

        if (jarsWithEntrypoint.size() != 1) {
            throw new IllegalStateException("Failed to find the raw minecraft client from the classpath");
        }

        //Get the minecraft jar (currently obfuscated)
        return jarsWithEntrypoint.iterator().next();
    }

    /**
     * Atomically moves the extracted embedded jar file to its final location.
     * If an atomic move is not supported, the file will be moved normally.
     */
    private static void moveInstallationIntoTarget(Path source, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create parent directory for installation " + source + " at " + destination, e);
        }

        try {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move installation " + source + " to its final location " + destination, e);
        }
    }
}
