/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

public record StartupArgs(
        File gameDirectory,
        String launchTarget,
        String[] programArgs,
        Set<File> claimedFiles,
        List<File> unclaimedClassPathEntries,
        boolean skipEntryPoint,
        @Nullable ClassLoader parentClassLoader) {
}
