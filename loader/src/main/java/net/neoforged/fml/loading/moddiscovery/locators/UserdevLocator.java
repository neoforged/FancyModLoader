/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.DevEnvUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

public class UserdevLocator implements IModFileCandidateLocator {
    private final Map<String, List<Path>> modFolders;

    public UserdevLocator(Map<String, List<Path>> modFolders) {
        this.modFolders = modFolders;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        var claimed = modFolders.values().stream().flatMap(List::stream).collect(Collectors.toCollection(HashSet::new));

        for (var modFolderGroup : modFolders.values()) {
            pipeline.addPath(modFolderGroup, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
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
