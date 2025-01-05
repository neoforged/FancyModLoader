/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.google.common.collect.Streams;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameLocator implements IModFileCandidateLocator {
    public static final String CLIENT_CLASS = "net/minecraft/client/Minecraft.class";
    private static final Logger LOG = LoggerFactory.getLogger(GameLocator.class);
    public static final String LIBRARIES_DIRECTORY_PROPERTY = "libraryDirectory";

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // 0) Vanilla Launcher puts the obfuscated jar on the classpath. We mark it as claimed to prevent it from
        // being hoisted into a module, occupying the entrypoint packages.
        preventLoadingOfObfuscatedClientJar(context);

        // Three possible ways to find the game:
        // 1a) It's exploded on the classpath
        // 1b) It's on the classpath, but as a jar
        var ourCl = Thread.currentThread().getContextClassLoader();

        var mcClassesRoot = ClasspathResourceUtils.findFileSystemRootOfFileOnClasspath(ourCl, CLIENT_CLASS);
        var mcResourceRoot = ClasspathResourceUtils.findFileSystemRootOfFileOnClasspath(ourCl, "assets/.mcassetsroot");
        if (mcClassesRoot != null && mcResourceRoot != null) {
            // Determine if we're dealing with a split jar-file situation (moddev)
            if (Files.isRegularFile(mcClassesRoot) && Files.isRegularFile(mcResourceRoot)) {
                context.addLocated(mcClassesRoot);
                context.addLocated(mcResourceRoot);
                addDevelopmentModFiles(List.of(mcClassesRoot), mcResourceRoot, pipeline);
                return;
            }

            // when the classesJar is a directory, we're assuming that we are in neo dev
            // in that case, we also need to find the resource directory
            if (Files.isDirectory(mcClassesRoot) && Files.isRegularFile(mcResourceRoot)) {
                // We look for all MANIFEST.MF directly on the classpath and try to find the one for NeoForge
                var manifestRoots = ClasspathResourceUtils.findFileSystemRootsOfFileOnClasspath(ourCl, JarModsDotTomlModFileReader.MANIFEST);
                Path resourcesRoot;
                for (var manifestRoot : manifestRoots) {
                    if (!Files.isDirectory(manifestRoot)) {
                        continue; // We're only interested in directories
                    }

                    if (isNeoForgeManifest(manifestRoot.resolve(JarModsDotTomlModFileReader.MANIFEST))) {
                        context.addLocated(mcClassesRoot);
                        context.addLocated(manifestRoot);
                        context.addLocated(mcResourceRoot);
                        addDevelopmentModFiles(List.of(mcClassesRoot, manifestRoot), mcResourceRoot, pipeline);
                        return;
                    }
                }
            }
        }

        // 2) In production it's in the libraries directory
        locateProductionMinecraft(context, pipeline);
    }

    private boolean isNeoForgeManifest(Path path) {
        // TODO: We should use some other build-time-only approach of marking the directories, same as we do for userdev
        try (var in = new BufferedInputStream(Files.newInputStream(path))) {
            var manifest = new Manifest(in);
            return "neoforge".equals(manifest.getMainAttributes().getValue("FML-System-Mods"));
        } catch (IOException e) {
            LOG.debug("Failed to read manifest at {}: {}", path, e);
            return false;
        }
    }

    /**
     * In production, the client and neoforge jars are assembled from partial jars in the libraries folder.
     */
    private static void locateProductionMinecraft(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // 2) It's neither, but a libraries directory and desired versions are given on the commandline
        var librariesDirectory = System.getProperty(LIBRARIES_DIRECTORY_PROPERTY);
        if (librariesDirectory == null) {
            LOG.error("When launching in production, the system property {} must point to the libraries directory.", LIBRARIES_DIRECTORY_PROPERTY);
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }

        var librariesRoot = Paths.get(librariesDirectory);
        if (!Files.isDirectory(librariesRoot)) {
            LOG.error("Libraries directory is not readable: {}", librariesRoot);
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }

        // The versions for Minecraft, NeoForge, etc. must be given on the CLI
        var versionInfo = FMLLoader.versionInfo();
        var minecraftVersion = versionInfo.mcVersion();
        if (minecraftVersion == null) {
            LOG.error("When launching in production, --fml.minecraftVersion must be present as a command-line argument");
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }
        var neoForgeVersion = versionInfo.neoForgeVersion();
        if (neoForgeVersion == null) {
            LOG.error("When launching in production, --fml.neoForgeVersion must be present as a command-line argument");
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }

        // Now try to find the actual artifacts
        var jarCoordinates = switch (context.getRequiredDistribution()) {
            case CLIENT -> getClientJarCoordinates(versionInfo);
            case DEDICATED_SERVER -> getServerJarCoordinates(versionInfo);
        };

        var minecraftJarContent = new ArrayList<Path>();
        if (!resolveLibraries(
                librariesRoot,
                minecraftJarContent,
                pipeline,
                jarCoordinates)) {
            return;
        }

        try {
            var mcJarContents = JarContents.of(minecraftJarContent);

            var mcJarMetadata = new ModJarMetadata(mcJarContents);
            var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
            var mcjar = IModFile.create(mcSecureJar, MinecraftModInfo::buildMinecraftModInfo);
            mcJarMetadata.setModFile(mcjar);
            pipeline.addModFile(mcjar);
        } catch (Exception e) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withCause(e));
        }

        // Always find and add the Neoforge jar as a "mod". This file contains the NeoForge classes and resources.
        var neoforgeCoordinate = new MavenCoordinate("net.neoforged", "neoforge", "", "universal", versionInfo.neoForgeVersion());
        var neoforgePath = librariesRoot.resolve(neoforgeCoordinate.toRelativeRepositoryPath());
        if (!Files.isRegularFile(neoforgePath)) {
            LOG.error("Couldn't find NeoForge at {}", neoforgePath);
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
        } else {
            pipeline.addPath(neoforgePath, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        }
    }

    private static MavenCoordinate[] getClientJarCoordinates(VersionInfo versionInfo) {
        // THE ORDER OF THESE ARTIFACTS MATTERS!
        // Classes in 'client' overwrite classes in 'srg'!
        return new MavenCoordinate[] {
                new MavenCoordinate("net.minecraft", "client", "", "srg", versionInfo.mcAndNeoFormVersion()),
                new MavenCoordinate("net.minecraft", "client", "", "extra", versionInfo.mcAndNeoFormVersion()),
                // This jar-file contains only the Minecraft classes patched by NeoForge
                new MavenCoordinate("net.neoforged", "neoforge", "", "client", versionInfo.neoForgeVersion())
        };
    }

    private static MavenCoordinate[] getServerJarCoordinates(VersionInfo versionInfo) {
        // THE ORDER OF THESE ARTIFACTS MATTERS!
        // Classes in 'client' overwrite classes in 'srg'!
        return new MavenCoordinate[] {
                new MavenCoordinate("net.minecraft", "server", "", "srg", versionInfo.mcAndNeoFormVersion()),
                new MavenCoordinate("net.minecraft", "server", "", "extra", versionInfo.mcAndNeoFormVersion()),
                // This jar-file contains only the Minecraft classes patched by NeoForge
                new MavenCoordinate("net.neoforged", "neoforge", "", "server", versionInfo.neoForgeVersion())
        };
    }

    private void preventLoadingOfObfuscatedClientJar(ILaunchContext context) {
        // TODO: Only relevant in dev

        try {
            var jarsWithEntrypoint = new HashSet<Path>();

            var resources = getClass().getClassLoader().getResources("net/minecraft/client/main/Main.class");
            while (resources.hasMoreElements()) {
                jarsWithEntrypoint.add(ClasspathResourceUtils.findJarPathFor("net/minecraft/client/main/Main.class", "minecraft jar", resources.nextElement()));
            }

            // This class would only be present in deobfuscated jars
            resources = getClass().getClassLoader().getResources("net/minecraft/client/Minecraft.class");
            while (resources.hasMoreElements()) {
                jarsWithEntrypoint.remove(ClasspathResourceUtils.findJarPathFor("net/minecraft/client/Minecraft.class", "minecraft jar", resources.nextElement()));
            }

            for (Path path : jarsWithEntrypoint) {
                LOG.info("Marking obfuscated client jar as claimed to prevent loading: {}", path);
                context.addLocated(path);
            }

        } catch (IOException ignored) {}
    }

    private static boolean resolveLibraries(Path libraryDirectory, List<Path> paths, IDiscoveryPipeline pipeline, MavenCoordinate... coordinates) {
        for (var coordinate : coordinates) {
            var path = libraryDirectory.resolve(coordinate.toRelativeRepositoryPath());
            if (!Files.isReadable(path)) {
                LOG.error("Couldn't find or read required Minecraft jar: {}.", path);
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
                return false;
            }
            paths.add(path);
        }

        return true;
    }

    private void addDevelopmentModFiles(List<Path> paths, Path minecraftResourcesRoot, IDiscoveryPipeline pipeline) {
        var packages = getNeoForgeSpecificPathPrefixes();

        var mcJarContents = new JarContentsBuilder()
                .paths(Streams.concat(paths.stream(), Stream.of(minecraftResourcesRoot)).toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    // We serve everything, except for things in the forge packages.
                    if (basePath.equals(minecraftResourcesRoot) || entry.endsWith("/")) {
                        return true;
                    }
                    // Any non-class file will be served from the client extra jar file mentioned above
                    if (!entry.endsWith(".class")) {
                        return false;
                    }
                    for (var pkg : packages) {
                        if (entry.startsWith(pkg)) {
                            return false;
                        }
                    }
                    return true;
                })
                .build();

        var mcJarMetadata = new ModJarMetadata(mcJarContents);
        var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
        var minecraftModFile = IModFile.create(mcSecureJar, MinecraftModInfo::buildMinecraftModInfo);
        mcJarMetadata.setModFile(minecraftModFile);
        pipeline.addModFile(minecraftModFile);

        // We need to separate out our resources/code so that we can show up as a different data pack.
        var neoforgeJarContents = new JarContentsBuilder()
                .paths(paths.toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    if (!entry.endsWith(".class")) return true;
                    for (var pkg : packages)
                        if (entry.startsWith(pkg)) return true;
                    return false;
                })
                .build();
        var modFile = JarModsDotTomlModFileReader.createModFile(neoforgeJarContents, ModFileDiscoveryAttributes.DEFAULT);
        if (modFile == null) {
            throw new IllegalStateException("Failed to construct a mod from the NeoForge classes and resources directories.");
        }
        pipeline.addModFile(modFile);
    }

    private static String[] getNeoForgeSpecificPathPrefixes() {
        return new String[] { "net/neoforged/neoforge/", "META-INF/services/", "META-INF/coremods.json", JarModsDotTomlModFileReader.MODS_TOML };
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public String toString() {
        return "game locator";
    }
}
