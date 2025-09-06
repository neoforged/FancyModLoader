/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Manifest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
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
    public static final String[] NEOFORGE_SPECIFIC_PATH_PREFIXES = { "net/neoforged/neoforge/", "META-INF/services/", JarModsDotTomlModFileReader.MODS_TOML };

    private static boolean isNotNeoForgeSpecificClass(String relativePath) {
        // Any non-class file will be served from the client extra jar file mentioned above
        if (!relativePath.endsWith(".class")) {
            return false;
        }
        for (var pkg : NEOFORGE_SPECIFIC_PATH_PREFIXES) {
            if (relativePath.startsWith(pkg)) {
                return false;
            }
        }
        return true;
    }

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
                addDevelopmentModFiles(List.of(mcClassesRoot), mcResourceRoot, context.getRequiredDistribution(), pipeline);
                return;
            }

            // when the classesJar is a directory, we're assuming that we are in neo dev
            // in that case, we also need to find the resource directory
            if (Files.isDirectory(mcClassesRoot) && Files.isRegularFile(mcResourceRoot)) {
                // We look for all MANIFEST.MF directly on the classpath and try to find the one for NeoForge
                var manifestRoots = ClasspathResourceUtils.findFileSystemRootsOfFileOnClasspath(ourCl, JarModsDotTomlModFileReader.MANIFEST);
                for (var manifestRoot : manifestRoots) {
                    if (!Files.isDirectory(manifestRoot)) {
                        continue; // We're only interested in directories
                    }

                    if (isNeoForgeManifest(manifestRoot.resolve(JarModsDotTomlModFileReader.MANIFEST))) {
                        context.addLocated(mcClassesRoot);
                        context.addLocated(manifestRoot);
                        context.addLocated(mcResourceRoot);
                        addDevelopmentModFiles(List.of(mcClassesRoot, manifestRoot), mcResourceRoot, context.getRequiredDistribution(), pipeline);
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

        var librariesRoot = Path.of(librariesDirectory);
        if (!Files.isDirectory(librariesRoot)) {
            LOG.error("Libraries directory is not readable: {}", librariesRoot);
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }

        // The versions for Minecraft and NeoForge, etc. must be given on the CLI
        var versions = context.getVersions();
        var minecraftVersion = versions.mcVersion();
        if (minecraftVersion == null) {
            LOG.error("When launching in production, --fml.minecraftVersion must be present as a command-line argument");
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }
        var neoForgeVersion = versions.neoForgeVersion();
        if (neoForgeVersion == null) {
            LOG.error("When launching in production, --fml.neoForgeVersion must be present as a command-line argument");
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }
        var neoFormVersion = versions.neoFormVersion();
        if (neoFormVersion == null) {
            LOG.error("When launching in production, --fml.neoFormVersion must be present as a command-line argument");
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation"));
            return;
        }

        // Detect if the newer Minecraft installation method is available. If not, we assume the old method.
        var patchedMinecraftPath = librariesRoot.resolve((switch (context.getRequiredDistribution()) {
            case CLIENT -> new MavenCoordinate("net.neoforged", "minecraft-client-patched", "", "", versions.neoForgeVersion());
            case DEDICATED_SERVER -> new MavenCoordinate("net.neoforged", "minecraft-server-patched", "", "", versions.neoForgeVersion());
        }).toRelativeRepositoryPath());

        if (Files.isRegularFile(patchedMinecraftPath)) {
            if (pipeline.addPath(patchedMinecraftPath, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE).isEmpty()) {
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar").withAffectedPath(patchedMinecraftPath));
            }
        } else {
            var content = new ArrayList<Path>();
            switch (context.getRequiredDistribution()) {
                case CLIENT -> {
                    addRequiredLibrary(new MavenCoordinate("net.minecraft", "client", "", "srg", versions.mcAndNeoFormVersion()), content);
                    addRequiredLibrary(new MavenCoordinate("net.minecraft", "client", "", "extra", versions.mcAndNeoFormVersion()), content);
                    addRequiredLibrary(new MavenCoordinate("net.neoforged", "neoforge", "", "client", versions.neoForgeVersion()), content);
                }
                case DEDICATED_SERVER -> {
                    addRequiredLibrary(new MavenCoordinate("net.minecraft", "server", "", "srg", versions.mcAndNeoFormVersion()), content);
                    addRequiredLibrary(new MavenCoordinate("net.minecraft", "server", "", "extra", versions.mcAndNeoFormVersion()), content);
                    addRequiredLibrary(new MavenCoordinate("net.neoforged", "neoforge", "", "server", versions.neoForgeVersion()), content);
                }
            }

            try {
                var mcJarContents = JarContents.ofPaths(content);

                var mcJarMetadata = new ModJarMetadata(mcJarContents);
                var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
                var mcjar = IModFile.create(mcSecureJar, new MinecraftModInfo(minecraftVersion)::buildMinecraftModInfo);
                mcJarMetadata.setModFile(mcjar);

                pipeline.addModFile(mcjar);
            } catch (Exception e) {
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withCause(e));
            }
        }

        var neoforgeCoordinate = new MavenCoordinate("net.neoforged", "neoforge", "", "universal", versions.neoForgeVersion());
        var neoforgeJar = librariesRoot.resolve(neoforgeCoordinate.toRelativeRepositoryPath());
        if (!Files.exists(neoforgeJar)) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.missing_neoforge_jar").withAffectedPath(neoforgeJar));
        }
        if (pipeline.addPath(neoforgeJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE).isEmpty()) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar").withAffectedPath(neoforgeJar));
        }
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

    private void addDevelopmentModFiles(List<Path> paths, Path minecraftResourcesRoot, Dist requiredDistribution, IDiscoveryPipeline pipeline) {
        var mcJarPaths = new ArrayList<JarContents.FilteredPath>();
        for (var path : paths) {
            mcJarPaths.add(new JarContents.FilteredPath(path, GameLocator::isNotNeoForgeSpecificClass));
        }

        var maskedPaths = new HashSet<String>();
        mcJarPaths.add(new JarContents.FilteredPath(minecraftResourcesRoot, relativePath -> {
            if (maskedPaths.contains(relativePath)) {
                LOG.debug("Masking access to {} since it's from a different Minecraft distribution.", relativePath);
                return false;
            }
            return true;
        }));

        JarContents mcJarContents;
        try {
            mcJarContents = JarContents.ofFilteredPaths(mcJarPaths);
        } catch (IOException e) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withCause(e));
            return;
        }

        // Figure out resources we have to filter out (i.e. if running a dedicated server)
        maskedPaths.addAll(NeoForgeDevDistCleaner.getMaskedFiles(mcJarContents, requiredDistribution)
                .filter(path -> !path.endsWith(".class"))
                .toList());

        var mcJarMetadata = new ModJarMetadata(mcJarContents);
        var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
        var minecraftModFile = IModFile.create(mcSecureJar, new MinecraftModInfo(null)::buildMinecraftModInfo);
        mcJarMetadata.setModFile(minecraftModFile);
        pipeline.addModFile(minecraftModFile);

        // We need to separate out our resources/code so that we can show up as a different data pack.
        var neoforgeJarPaths = new ArrayList<JarContents.FilteredPath>();
        for (var path : paths) {
            neoforgeJarPaths.add(new JarContents.FilteredPath(path, relativePath -> {
                if (!relativePath.endsWith(".class")) {
                    return true;
                }
                for (var includedPrefix : NEOFORGE_SPECIFIC_PATH_PREFIXES) {
                    if (relativePath.startsWith(includedPrefix)) {
                        return true;
                    }
                }
                return false;
            }));
        }
        JarContents neoforgeJarContents;
        try {
            neoforgeJarContents = JarContents.ofFilteredPaths(neoforgeJarPaths);
        } catch (IOException e) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withCause(e));
            return;
        }
        var modFile = JarModsDotTomlModFileReader.createModFile(neoforgeJarContents, ModFileDiscoveryAttributes.DEFAULT);
        if (modFile == null) {
            throw new IllegalStateException("Failed to construct a mod from the NeoForge classes and resources directories.");
        }
        pipeline.addModFile(modFile);
    }

    private static void addRequiredLibrary(MavenCoordinate coordinate, List<Path> content) {
        var path = LibraryFinder.findPathForMaven(coordinate);
        if (!Files.exists(path)) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withAffectedPath(path));
        } else {
            content.add(path);
        }
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
