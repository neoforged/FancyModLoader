/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public record StartupArgs(
        File gameDirectory,
        String launchTarget,
        String[] programArgs,
        Set<File> claimedFiles,
        List<File> unclaimedClassPathEntries,
        boolean skipEntryPoint,
        @Nullable ClassLoader parentClassLoader) {
    public Path cacheRoot() {
        return gameDirectory.toPath().resolve(".neoforgecache");
    }
}
