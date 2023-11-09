/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.VersionInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class FMLServerLaunchHandler extends CommonServerLaunchHandler {
    @Override public String name() { return "fmlserver"; }

    @Override
    protected BiPredicate<String, String> processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, BiPredicate<String, String> filter, Stream.Builder<List<Path>> mods) {
        var fmlonly = LibraryFinder.findPathForMaven("net.neoforged.fml", "fmlonly", "", "universal", versionInfo.mcAndFmlVersion());
        mods.add(List.of(fmlonly));
        return filter;
    }
}
