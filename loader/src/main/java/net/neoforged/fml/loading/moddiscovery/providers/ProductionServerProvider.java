/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileProvider;
import net.neoforged.neoforgespi.locating.LoadResult;

/**
 * Locates the Minecraft server files in a production environment.
 */
public class ProductionServerProvider implements IModFileProvider, ISystemModSource {
    private final List<MavenCoordinate> additionalContent;

    public ProductionServerProvider(List<MavenCoordinate> additionalContent) {
        this.additionalContent = additionalContent;
    }

    @Override
    public String name() {
        return "minecraft server (production)";
    }

    @Override
    public List<LoadResult<IModFile>> provideModFiles(ILaunchContext launchContext) {
        var vers = FMLLoader.versionInfo();

        try {
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

            var content = new ArrayList<Path>();
            content.add(mc);
            content.add(mcextra_filtered.getRootPath());
            for (var artifact : additionalContent) {
                content.add(LibraryFinder.findPathForMaven(artifact));
            }

            var mcJarContents = JarContents.of(content);

            var mcJarMetadata = new ModJarMetadata(mcJarContents);
            var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
            var mcjar = IModFile.create(mcSecureJar, this, MinecraftModInfo::buildMinecraftModInfo, null);
            mcJarMetadata.setModFile(mcjar);

            return List.of(new LoadResult.Success<>(mcjar));
        } catch (Exception e) {
            // TODO Translation
            return List.of(new LoadResult.Error<>(ModLoadingIssue.error("corrupted_file", e.toString())));
        }
    }
}
