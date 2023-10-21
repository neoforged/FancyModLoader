/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import cpw.mods.modlauncher.api.ILaunchHandlerService;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.VersionInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class ForgeServerLaunchHandler extends CommonServerLaunchHandler implements ILaunchHandlerService {
    @Override public String name() { return "forgeserver"; }

    @Override
    protected BiPredicate<String, String> processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, BiPredicate<String, String> filter, Stream.Builder<List<Path>> mods) {
        var forgepatches = LibraryFinder.findPathForMaven("net.neoforged", "neoforge", "", "server", versionInfo.mcAndNeoForgeVersion());
        var forgejar = LibraryFinder.findPathForMaven("net.neoforged", "neoforge", "", "universal", versionInfo.mcAndNeoForgeVersion());
        mc.add(forgepatches);
        mods.add(List.of(forgejar));
        return filter;
    }
}
