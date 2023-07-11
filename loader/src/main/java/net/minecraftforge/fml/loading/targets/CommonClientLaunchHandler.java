/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.targets;

import cpw.mods.modlauncher.api.ServiceRunner;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LibraryFinder;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class CommonClientLaunchHandler extends CommonLaunchHandler {
    @Override public Dist getDist()  { return Dist.CLIENT; }
    @Override public String getNaming() { return "srg"; }
    @Override public boolean isProduction() { return true; }

    @Override
    protected ServiceRunner makeService(final String[] arguments, final ModuleLayer gameLayer) {
        return ()->clientService(arguments, gameLayer);
    }

    @Override
    public LocatedPaths getMinecraftPaths() {
        final var vers = FMLLoader.versionInfo();
        var mc = LibraryFinder.findPathForMaven("net.minecraft", "client", "", "srg", vers.mcAndMCPVersion());
        var mcextra = LibraryFinder.findPathForMaven("net.minecraft", "client", "", "extra", vers.mcAndMCPVersion());
        var fmlevents = LibraryFinder.findPathForMaven("net.neoforged.fancymodloader", "events", "", "", vers.fmlVersion());
        var mcstream = Stream.<Path>builder().add(mc).add(mcextra).add(fmlevents);
        var modstream = Stream.<List<Path>>builder();

        processMCStream(vers, mcstream, modstream);

        var fmlcore = LibraryFinder.findPathForMaven("net.neoforged.fancymodloader", "core", "", "", vers.fmlVersion());
        var javafmllang = LibraryFinder.findPathForMaven("net.neoforged.fancymodloader", "language-java", "", "", vers.fmlVersion());
        var lowcodelang = LibraryFinder.findPathForMaven("net.neoforged.fancymodloader", "language-lowcode", "", "", vers.fmlVersion());
        var mclang = LibraryFinder.findPathForMaven("net.neoforged.fancymodloader", "language-minecraft", "", "", vers.fmlVersion());

        return new LocatedPaths(mcstream.build().toList(), (a,b) -> true, modstream.build().toList(), List.of(fmlcore, javafmllang, lowcodelang, mclang));
    }

    protected abstract void processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods);
}
