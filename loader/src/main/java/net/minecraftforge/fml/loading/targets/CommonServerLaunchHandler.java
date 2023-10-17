/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.targets;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LibraryFinder;
import net.minecraftforge.fml.loading.VersionInfo;
import net.neoforged.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public abstract class CommonServerLaunchHandler extends CommonLaunchHandler {
    @Override public Dist getDist()  { return Dist.DEDICATED_SERVER; }
    @Override public String getNaming() { return "srg"; }
    @Override public boolean isProduction() { return true; }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        serverService(arguments, gameLayer);
    }

    @Override
    public LocatedPaths getMinecraftPaths() {
        final var vers = FMLLoader.versionInfo();
        var mc = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "srg", vers.mcAndMCPVersion());
        var mcextra = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "extra", vers.mcAndMCPVersion());
        var mcextra_filtered = SecureJar.from( // We only want it for it's resources. So filter everything else out.
            (path, base) -> {
                return path.equals("META-INF/versions/") || // This is required because it bypasses our filter for the manifest, and it's a multi-release jar.
                     (!path.endsWith(".class") &&
                      !path.startsWith("META-INF/"));
            }, mcextra
        );
        BiPredicate<String, String> filter = (path, base) -> true;
        BiPredicate<String, String> nullFilter = filter;

        var mcstream = Stream.<Path>builder().add(mc).add(mcextra_filtered.getRootPath());
        var modstream = Stream.<List<Path>>builder();

        filter = processMCStream(vers, mcstream, filter, modstream);

        // use this hack instead of setting filter to null initially for backwards compatibility if anything overrides
        // processMCStream with a custom filter
        if (filter == nullFilter)
            filter = null;

        return new LocatedPaths(mcstream.build().toList(), filter, modstream.build().toList(), this.getFmlPaths(this.getLegacyClasspath()));
    }

    protected abstract BiPredicate<String, String> processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, BiPredicate<String, String> filter, Stream.Builder<List<Path>> mods);
}
