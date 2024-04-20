/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import cpw.mods.jarhandling.JarContents;
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
 * Locates the Minecraft client files in a production environment.
 * <p>
 * It assumes that the installer produced two artifacts, one containing the Minecraft classes ("srg") which have
 * been renamed to Mojangs official names using their mappings, and another containing only the Minecraft resource
 * files ("extra"), and searches for these artifacts in the library directory.
 */
public class ProductionClientProvider implements IModFileProvider, ISystemModSource {
    private final List<MavenCoordinate> additionalContent;

    public ProductionClientProvider(List<MavenCoordinate> additionalContent) {
        this.additionalContent = additionalContent;
    }

    @Override
    public List<LoadResult<IModFile>> provideModFiles(ILaunchContext launchContext) {
        var vers = FMLLoader.versionInfo();

        try {
            var content = new ArrayList<Path>();
            content.add(LibraryFinder.findPathForMaven("net.minecraft", "client", "", "srg", vers.mcAndNeoFormVersion()));
            content.add(LibraryFinder.findPathForMaven("net.minecraft", "client", "", "extra", vers.mcAndNeoFormVersion()));
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
            return List.of(new LoadResult.Error<>(ModLoadingIssue.error("corrupted_file", e.getMessage())));
        }
    }

    @Override
    public String toString() {
        var result = new StringBuilder("production client provider");
        for (var mavenCoordinate : additionalContent) {
            result.append(" +").append(mavenCoordinate);
        }
        return result.toString();
    }
}
