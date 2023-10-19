/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class CommonClientLaunchHandler extends CommonLaunchHandler {
    @Override public Dist getDist()  { return Dist.CLIENT; }
    @Override public String getNaming() { return "srg"; }
    @Override public boolean isProduction() { return true; }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        clientService(arguments, gameLayer);
    }

    @Override
    public LocatedPaths getMinecraftPaths() {
        final var vers = FMLLoader.versionInfo();
        var mc = LibraryFinder.findPathForMaven("net.minecraft", "client", "", "srg", vers.mcAndMCPVersion());
        var mcextra = LibraryFinder.findPathForMaven("net.minecraft", "client", "", "extra", vers.mcAndMCPVersion());
        var mcstream = Stream.<Path>builder().add(mc).add(mcextra);
        var modstream = Stream.<List<Path>>builder();

        processMCStream(vers, mcstream, modstream);

        return new LocatedPaths(mcstream.build().toList(), null, modstream.build().toList(), this.getFmlPaths(this.getLegacyClasspath()));
    }

    protected abstract void processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods);
}
