/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuiltinGameLibraryLocator extends AbstractJarFileModLocator {
    private final List<Path> legacyClasspath = AbstractJarFileModLocator.getLegacyClasspath();

    @Override
    public String name() {
        return "builtin game layer libraries";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    @Override
    public Stream<Path> scanCandidates() {
        String gameLibrariesStr = System.getProperty("fml.gameLayerLibraries");
        if (gameLibrariesStr == null || gameLibrariesStr.isBlank())
            return Stream.of();

        Set<Path> targets = Arrays.stream(gameLibrariesStr.split(",")).map(Path::of).collect(Collectors.toSet());
        var paths = Stream.<Path>builder();

        for (Path path : this.legacyClasspath) {
            if (targets.contains(path.getFileName()))
                paths.add(path);
        }

        return paths.build();
    }
}
