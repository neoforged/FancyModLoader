/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.fml.loading.VersionInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class ForgeUserdevLaunchHandler extends CommonUserdevLaunchHandler {
    @Override
    protected void processStreams(String[] classpath, VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods) {
        var forge = searchJarOnClasspath(classpath, "neoforge-" + versionInfo.neoForgeVersion());
        if (forge.isEmpty()) {
            throw new RuntimeException("Could not find %s, nor %s jar on classpath".formatted("neoforge-" + versionInfo.neoForgeVersion(), "neoforge-" + versionInfo.neoForgeVersion()));
        }
        mc.add(forge.get());
    }
}
