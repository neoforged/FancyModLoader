/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.VersionInfo;

public abstract class CommonServerLaunchHandler extends CommonLaunchHandler {
    @Override
    public Dist getDist() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public boolean isProduction() {
        return true;
    }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        serverService(arguments, gameLayer);
    }

    @Override
    public LocatedPaths getMinecraftPaths() {
        final var vers = FMLLoader.versionInfo();
        var mc = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "srg", vers.mcAndNeoFormVersion());
        var mcextra = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "extra", vers.mcAndNeoFormVersion());
        var mcextra_filtered = SecureJar.from(new JarContentsBuilder()
                // We only want it for its resources. So filter everything else out.
                .pathFilter((path, base) -> {
                    return path.equals("META-INF/versions/") || // This is required because it bypasses our filter for the manifest, and it's a multi-release jar.
                            (!path.endsWith(".class") &&
                                    !path.startsWith("META-INF/"));
                })
                .paths(mcextra)
                .build());

        var mcstream = Stream.<Path>builder().add(mc).add(mcextra_filtered.getRootPath());
        var modstream = Stream.<List<Path>>builder();

        processMCStream(vers, mcstream, modstream);

        return new LocatedPaths(mcstream.build().toList(), null, modstream.build().toList(), this.getFmlPaths(this.getLegacyClasspath()));
    }

    protected abstract void processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, Stream.Builder<List<Path>> mods);
}
