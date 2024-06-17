/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.DevEnvUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UserdevLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<String, List<Path>> modFolders;

    public UserdevLocator(Map<String, List<Path>> modFolders) {
        this.modFolders = modFolders;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

        // Index the JVM classpath
        var entriesOnClasspath = new HashSet<>();
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
            try {
                var path = Paths.get(entry);
                entriesOnClasspath.add(path);
            } catch (Exception ignored) {
            }
        }

        var claimed = modFolders.values().stream().flatMap(List::stream).collect(Collectors.toCollection(HashSet::new));

        for (var entry : modFolders.entrySet()) {
            var key = entry.getKey();
            var groupedFolders = entry.getValue();
            var existingFolders = groupedFolders.stream().filter(entriesOnClasspath::contains).toList();
            if (existingFolders.isEmpty()) {
                LOGGER.warn("Mod folder group {} matches no folders on the classpath", key);
                continue;
            }

            pipeline.addPath(existingFolders, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        }

        var fromClasspath = new ArrayList<Path>();
        fromClasspath.addAll(DevEnvUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MODS_TOML));
        fromClasspath.addAll(DevEnvUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MANIFEST));
        for (var path : fromClasspath) {
            if (claimed.add(path)) {
                pipeline.addPath(List.of(path), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY);
            }
        }
    }

    @Override
    public String toString() {
        return "userdev mods and services";
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY;
    }
}
