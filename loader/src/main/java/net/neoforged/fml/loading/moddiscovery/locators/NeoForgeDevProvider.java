/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.google.common.collect.Streams;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.DevEnvUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the Minecraft and NeoForge mods in a NeoForge dev environment.
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
            minecraftResourcesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("assets/.mcassetsroot");
        }

        var packages = getNeoForgeSpecificPathPrefixes();
        var minecraftResourcesPrefix = minecraftResourcesRoot;

        var maskedPaths = new HashSet<String>();
        var mcJarContents = new JarContentsBuilder()
                .paths(Streams.concat(paths.stream(), Stream.of(minecraftResourcesRoot)).toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    if (maskedPaths.contains(entry)) {
                        LOG.debug("Masking access to {} since it's from a different Minecraft distribution.", entry);
                        return false;
                    }
                    // We serve everything, except for things in the forge packages.
                    if (basePath.equals(minecraftResourcesPrefix) || entry.endsWith("/")) {
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

        var neoForgeDevDistCleaner = (NeoForgeDevDistCleaner) context.environment().findLaunchPlugin("neoforgedevdistcleaner").orElseThrow();

        loadMaskedFiles(mcJarContents, maskedPaths, neoForgeDevDistCleaner);

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
        pipeline.addModFile(JarModsDotTomlModFileReader.createModFile(neoforgeJarContents, ModFileDiscoveryAttributes.DEFAULT));
    }

    private void loadMaskedFiles(JarContents minecraftJar, Set<String> maskedPaths, NeoForgeDevDistCleaner neoForgeDevDistCleaner) {
        var manifest = minecraftJar.getManifest();
        String dists = manifest.getMainAttributes().getValue(NAME_DISTS);
        if (dists == null) {
            return; // Jar has no masking attributes
        }
        var dist = switch (FMLLoader.getDist()) {
            case CLIENT -> "client";
            case DEDICATED_SERVER -> "server";
        };
        if (Arrays.stream(dists.split("\\s+")).allMatch(s -> s.equals(dist))) {
            return; // Jar contains only markers for the current dist anyway
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

        neoForgeDevDistCleaner.stripClasses(strippedClasses);
    }

    private static String[] getNeoForgeSpecificPathPrefixes() {
        return new String[] { "net/neoforged/neoforge/", "META-INF/services/", "META-INF/coremods.json", JarModsDotTomlModFileReader.MODS_TOML };
    }

    @Override
    public String toString() {
        return "neoforge devenv provider (" + paths + ")";
    }
}
