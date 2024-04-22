/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.loading.moddiscovery.providers.DevEnvUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;

public class UserdevLocator implements IModFileCandidateLocator {
    private final Map<String, List<Path>> modFolders;

    public UserdevLocator(Map<String, List<Path>> modFolders) {
        this.modFolders = modFolders;
    }

    @Override
    public String name() {
        return "userdev mods and services";
    }

    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext context) {
        var claimed = modFolders.values().stream().flatMap(List::stream).collect(Collectors.toCollection(HashSet::new));

        var result = Stream.<List<Path>>builder();
        modFolders.values().forEach(result::add);

        Arrays.stream(System.getProperty("legacyClassPath", "").split(File.pathSeparator))
                .map(Path::of)
                .filter(path -> !context.isLocated(path))
                .map(List::of)
                .forEach(result::add);

        var fromClasspath = new ArrayList<Path>();
        fromClasspath.addAll(DevEnvUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MODS_TOML));
        fromClasspath.addAll(DevEnvUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MANIFEST));
        for (var path : fromClasspath) {
            if (claimed.add(path)) {
                result.add(List.of(path));
            }
        }

        return result.build().map(IModFileCandidateLocator::result);
    }
}
