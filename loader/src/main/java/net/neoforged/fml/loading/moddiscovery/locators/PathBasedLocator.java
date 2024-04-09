/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;

/**
 * "Locates" mods from a fixed set of paths.
 */
public record PathBasedLocator(String name, List<Path> paths) implements IModFileCandidateLocator {
    public PathBasedLocator(String name, Path... paths) {
        this(name, List.of(paths));
    }

    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext context) {
        return paths.stream().map(path -> {
            try {
                return new LoadResult.Success<>(JarContents.of(path));
            } catch (Exception e) {
                // TODO translation
                return new LoadResult.Error<>(ModLoadingIssue.error("corrupted_file", e));
            }
        });
    }
}
