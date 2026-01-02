/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import static net.neoforged.fml.loading.game.GameDiscovery.LIBRARIES_DIRECTORY_PROPERTY;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.internal.binarypatchapplier.PatchBase;
import net.neoforged.internal.binarypatchapplier.Patcher;
import org.jetbrains.annotations.Nullable;

class AutoInstallationService {
    private static final String AUTOINSTALL_PATCH_LOCATION = "autoinstall.patches";

    @Nullable
    static Path discoverOrInstall(Dist requiredDist, String neoForgeVersion, ClassLoader classLoader) throws IOException {
        if (!requiredDist.isClient())
            return null;

        var progress = StartupNotificationManager.addProgressBar("Installation", 3);
        progress.label("Installation - Extracting resources...");

        var tempDir = Files.createTempDirectory("fml-auto-installer");
        var patchesPath = extractPatchesPath(classLoader, tempDir);
        if (patchesPath == null) {
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
                List.of(patchesPath.toFile()),
                output.toFile(),
                (ignored) -> {} //We are not outputting debugging information at the moment.
        );

        progress.increment();
        progress.label("Installation - Finalizing changes...");

        var patchedMinecraftPath = copyToLibraries(neoForgeVersion, requiredDist, output);

        progress.increment();
        progress.complete();

        return patchedMinecraftPath;
    }

    private static Path copyToLibraries(final String neoForgeVersion, final Dist dist, final Path output) throws IOException {
        var librariesDirectory = System.getProperty(LIBRARIES_DIRECTORY_PROPERTY);
        var patchedMinecraftPath = Path.of(librariesDirectory).resolve((switch (dist) {
            case CLIENT -> new MavenCoordinate("net.neoforged", "minecraft-client-patched", "", "", neoForgeVersion);
            case DEDICATED_SERVER -> new MavenCoordinate("net.neoforged", "minecraft-server-patched", "", "", neoForgeVersion);
        }).toRelativeRepositoryPath());

        Files.createDirectories(patchedMinecraftPath.getParent());
        Files.copy(output, patchedMinecraftPath);
        return patchedMinecraftPath;
    }

    @Nullable
    private static Path extractPatchesPath(ClassLoader classLoader, Path tempDir) {
        try (var in = classLoader.getResourceAsStream("net/neoforged/neoforge/common/version.properties")) {
            if (in == null) {
                return null;
            }

            Properties properties = new Properties();
            properties.load(new BufferedInputStream(in));

            if (!properties.contains(AUTOINSTALL_PATCH_LOCATION))
                return null;

            var patchesInnerPath = properties.get(AUTOINSTALL_PATCH_LOCATION).toString();
            return extractFrom(tempDir, "patchers.lzma", patchesInnerPath);
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

        // This class would only be present in deobfuscated jars
        resources = ourCl.getResources("net/minecraft/client/Minecraft.class");
        while (resources.hasMoreElements()) {
            jarsWithEntrypoint.remove(ClasspathResourceUtils.findJarPathFor("net/minecraft/client/Minecraft.class", "minecraft jar", resources.nextElement()));
        }

        if (jarsWithEntrypoint.size() != 1) {
            throw new IllegalStateException("Failed to find the raw minecraft client from the classpath");
        }

        //Get the minecraft jar (currently obfuscated)
        return jarsWithEntrypoint.iterator().next();
    }

    private static Path extractFrom(final Path tempDir, final String targetName, final String packagedName) throws IOException {
        var targetFile = tempDir.resolve(targetName);
        var patchResource = AutoInstallationService.class.getResource(packagedName);
        if (patchResource == null) {
            throw new IllegalStateException("Could not find %s in the auto installer.".formatted(packagedName));
        }

        try (BufferedInputStream in = new BufferedInputStream(patchResource.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile.toFile())) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }

        return targetFile;
    }
}
