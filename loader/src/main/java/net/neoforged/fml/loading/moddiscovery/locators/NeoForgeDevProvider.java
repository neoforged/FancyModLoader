/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.DevEnvUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the Minecraft and NeoForge mods in a NeoForge dev environment or a mod dev environment.
 */
public class NeoForgeDevProvider implements IModFileCandidateLocator {
    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeDevProvider.class);
    private static final Attributes.Name NAME_DISTS = new Attributes.Name("Minecraft-Dists");
    private static final Attributes.Name NAME_DIST = new Attributes.Name("Minecraft-Dist");

    private final List<Path> paths;

    public NeoForgeDevProvider(List<Path> paths) {
        this.paths = paths;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        Path minecraftResourcesRoot = null;

        // try finding client-extra jar explicitly first
        var legacyClassPath = System.getProperty("legacyClassPath");
        if (legacyClassPath != null) {
            minecraftResourcesRoot = Arrays.stream(legacyClassPath.split(File.pathSeparator))
                    .map(Path::of)
                    .filter(path -> path.getFileName().toString().contains("client-extra"))
                    .findFirst()
                    .orElse(null);
        }
        // then fall back to finding it on the current classpath
        if (minecraftResourcesRoot == null) {
            minecraftResourcesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("data/.mcassetsroot");

            // Heuristic: If only a single path is given in this.paths, and the minecraft resource root
            // Is in that same file, we assume we're in a "new" environment where Minecraft and neoforge jars are split
            if (this.paths.size() == 1 && this.paths.getFirst().equals(minecraftResourcesRoot)) {
                LOG.info("Assuming Minecraft Jar is: {}", minecraftResourcesRoot);
                var modFile = pipeline.addPath(minecraftResourcesRoot, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE).orElse(null);
                if (modFile == null) {
                    pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar"));
                } else if (modFile.getModInfos().isEmpty() || !modFile.getModInfos().getFirst().getModId().equals("minecraft")) {
                    throw new IllegalStateException("The detected Minecraft jar " + minecraftResourcesRoot + " does not contain a mod identifying as 'minecraft'. It contains: " + modFile.getModInfos());
                }
                return;
            }
        }

        var packages = getNeoForgeSpecificPathPrefixes();

        var maskedPaths = new HashSet<String>();
        var filteredPaths = new ArrayList<JarContents.FilteredPath>();
        for (var path : paths) {
            filteredPaths.add(new JarContents.FilteredPath(path, relativePath -> {
                if (maskedPaths.contains(relativePath)) {
                    LOG.debug("Masking access to {} since it's from a different Minecraft distribution.", relativePath);
                    return false;
                }
                // Any non-class file will be served from the client extra jar file mentioned above
                if (!relativePath.endsWith(".class")) {
                    return false;
                }
                for (var pkg : packages) {
                    if (relativePath.startsWith(pkg)) {
                        return false;
                    }
                }
                return true;
            }));
        }
        filteredPaths.add(new JarContents.FilteredPath(minecraftResourcesRoot, relativePath -> {
            if (maskedPaths.contains(relativePath)) {
                LOG.debug("Masking access to {} since it's from a different Minecraft distribution.", relativePath);
                return false;
            }
            return true;
        }));

        JarContents mcJarContents;
        try {
            mcJarContents = JarContents.ofFilteredPaths(filteredPaths);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build merged Minecraft jar", e);
        }

        var neoForgeDevDistCleaner = (NeoForgeDevDistCleaner) context.environment().findClassProcessor(NeoForgeDevDistCleaner.NAME).orElseThrow();

        loadMaskedFiles(mcJarContents, maskedPaths, neoForgeDevDistCleaner, pipeline);

        var mcJarMetadata = new ModJarMetadata(mcJarContents);
        var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
        var minecraftModFile = IModFile.create(mcSecureJar, MinecraftModInfo::buildMinecraftModInfo);
        mcJarMetadata.setModFile(minecraftModFile);
        pipeline.addModFile(minecraftModFile);

        // We need to separate out our resources/code so that we can show up as a different data pack.
        JarContents neoforgeJarContents;
        try {
            neoforgeJarContents = JarContents.ofFilteredPaths(paths.stream().map(path -> new JarContents.FilteredPath(path, relativePath -> {
                if (!relativePath.endsWith(".class")) return true;
                for (var pkg : packages)
                    if (relativePath.startsWith(pkg)) return true;
                return false;
            })).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build merged NeoForge jar", e);
        }
        pipeline.addModFile(JarModsDotTomlModFileReader.createModFile(neoforgeJarContents, ModFileDiscoveryAttributes.DEFAULT));
    }

    /**
     * Loads file masking information from the jar's manifest, masking resource files that should not be present and
     * telling {@link NeoForgeDevDistCleaner} to clean class files that should be masked.
     */
    private void loadMaskedFiles(JarContents minecraftJar, Set<String> maskedPaths, NeoForgeDevDistCleaner neoForgeDevDistCleaner, IDiscoveryPipeline pipeline) {
        var manifest = minecraftJar.getManifest();
        if (manifest == null) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.neodev_missing_dists_attribute", NAME_DISTS));
            return; // Jar has no masking attributes; in dev, this is necessary
        }
        String dists = manifest.getMainAttributes().getValue(NAME_DISTS);
        if (dists == null) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.neodev_missing_dists_attribute", NAME_DISTS));
            return; // Jar has no masking attributes; in dev, this is necessary
        }
        var dist = switch (FMLLoader.getDist()) {
            case CLIENT -> "client";
            case DEDICATED_SERVER -> "server";
        };
        if (Arrays.stream(dists.split("\\s+")).allMatch(s -> s.equals(dist))) {
            return; // Jar contains only markers for the current dist anyway
        }
        if (Arrays.stream(dists.split("\\s+")).noneMatch(s -> s.equals(dist))) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.neodev_missing_appropriate_dist", dist, NAME_DISTS));
            return; // Jar has no marker for the current dist; this is wacky and should not occur
        }

        Set<String> strippedClasses = new HashSet<>();

        for (var entry : manifest.getEntries().entrySet()) {
            var filePath = entry.getKey();
            var fileDist = entry.getValue().getValue(NAME_DIST);
            if (fileDist != null && !fileDist.equals(dist)) {
                // Classes are kept, but set to be filtered out at runtime; resources are removed entirely.
                if (filePath.endsWith(".class")) {
                    var className = filePath.substring(0, filePath.length() - ".class".length()).replace('/', '.');
                    strippedClasses.add(className);
                } else {
                    maskedPaths.add(filePath);
                }
            }
        }

        neoForgeDevDistCleaner.maskClasses(strippedClasses);
    }

    private static String[] getNeoForgeSpecificPathPrefixes() {
        return new String[] { "net/neoforged/neoforge/", "META-INF/services/", JarModsDotTomlModFileReader.MODS_TOML };
    }

    @Override
    public String toString() {
        return "neoforge devenv provider (" + paths + ")";
    }
}
