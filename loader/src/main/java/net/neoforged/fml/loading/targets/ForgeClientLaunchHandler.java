/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.VersionInfo;

public class ForgeClientLaunchHandler extends CommonClientLaunchHandler {
    @Override public String name() { return "forgeclient"; }

    @Override
    protected void processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods) {
        var forgepatches = LibraryFinder.findPathForMaven(versionInfo.neoForgeGroup(), "neoforge", "", "client", versionInfo.neoForgeVersion());
        var forgejar = LibraryFinder.findPathForMaven(versionInfo.neoForgeGroup(), "neoforge", "", "universal", versionInfo.neoForgeVersion());
        mc.add(forgepatches);
        mods.add(List.of(forgejar));
    }
}
