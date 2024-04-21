/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.loading.moddiscovery.providers.DevEnvUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;
import org.slf4j.Logger;

public class UserdevClasspathLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    record PathGroup(String name, List<Path> paths) {}

    // Maps a Path to the group of paths it belongs to.
    private final Map<Path, PathGroup> pathGrouping;

    public UserdevClasspathLocator(Map<String, List<Path>> modFolders) {
        this.pathGrouping = modFolders.entrySet().stream()
                .map(entry -> new PathGroup(entry.getKey(), entry.getValue()))
                .flatMap(group -> group.paths().stream().map(path -> Map.entry(path, group)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public String name() {
        return "userdev classpath";
    }

    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext context) {
        // Find all declared mods on the classpath
        var potentialPaths = new HashSet<Path>();
        potentialPaths.addAll(DevEnvUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MODS_TOML));
        potentialPaths.addAll(DevEnvUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MANIFEST));

        // Of all the potential paths, group them by their path-grouping (or use an anonymous group if nothing was defined)
        // And replace each by the set of paths in the group.
        // This causes a resources directory that contains a mods TOML file to be picked up alongside its classes directory.
        return potentialPaths.stream()
                .collect(Collectors.groupingBy(
                        path -> pathGrouping.getOrDefault(path, new PathGroup("ungrouped", List.of(path)))))
                .keySet()
                .stream()
                .map(PathGroup::paths)
                .map(IModFileCandidateLocator::result);
    }
}
