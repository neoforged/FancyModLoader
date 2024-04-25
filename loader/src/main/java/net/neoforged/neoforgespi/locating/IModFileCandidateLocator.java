/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import cpw.mods.jarhandling.JarContents;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator;
import net.neoforged.neoforgespi.ILaunchContext;

/**
 * Loaded as a ServiceLoader. Takes mechanisms for locating candidate "mod" JARs.
 */
public interface IModFileCandidateLocator {
    /**
     * Creates an IModFileCandidateLocator that searches for mod jar-files in the given filesystem location.
     */
    static IModFileCandidateLocator forFolder(File folder, String identifier) {
        return new ModsFolderLocator(folder.toPath(), identifier);
    }

    static LoadResult<JarContents> result(Path path) {
        try {
            return result(JarContents.of(path));
        } catch (Exception e) {
            // TODO translation
            return new LoadResult.Error<>(ModLoadingIssue.error("corrupted_file", path, e.toString()));
        }
    }

    static LoadResult<JarContents> result(List<Path> paths) {
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one path");
        }
        try {
            return result(JarContents.of(paths));
        } catch (Exception e) {
            // TODO translation
            return new LoadResult.Error<>(ModLoadingIssue.error("corrupted_file", paths.getFirst(), e.toString()));
        }
    }

    static LoadResult<JarContents> result(JarContents contents) {
        return new LoadResult.Success<>(contents);
    }

    /**
     * {@return all mod paths that this mod locator can find. the stream must be closed by the caller}
     */
    void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline);
}
