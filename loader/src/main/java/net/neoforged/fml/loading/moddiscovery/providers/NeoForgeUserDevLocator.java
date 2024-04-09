/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import cpw.mods.jarhandling.JarContents;
import java.util.stream.Stream;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;

/**
 * Finds the Neoforge jar on the classpath and provides it as a mod.
 */
public class NeoForgeUserDevLocator implements IModFileCandidateLocator, ISystemModSource {
    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext launchContext) {
        var neoforgeJar = DevEnvUtils.findFileSystemRootOfFileOnClasspath("net/neoforged/neoforge/common/NeoForgeMod.class");

        return Stream.of(IModFileCandidateLocator.result(neoforgeJar));
    }

    @Override
    public String name() {
        return "neoforge (userdev)";
    }
}
