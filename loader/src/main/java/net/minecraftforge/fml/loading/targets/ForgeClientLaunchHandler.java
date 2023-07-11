/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.targets;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import net.minecraftforge.fml.loading.LibraryFinder;
import net.minecraftforge.fml.loading.VersionInfo;

public class ForgeClientLaunchHandler extends CommonClientLaunchHandler {
    @Override public String name() { return "forgeclient"; }

    @Override
    protected void processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods) {
        var forgepatches = LibraryFinder.findPathForMaven("net.neoforged", "forge", "", "client", versionInfo.mcAndForgeVersion());
        var forgejar = LibraryFinder.findPathForMaven("net.neoforged", "forge", "", "universal", versionInfo.mcAndForgeVersion());
        mc.add(forgepatches);
        mods.add(List.of(forgejar));
    }
}
