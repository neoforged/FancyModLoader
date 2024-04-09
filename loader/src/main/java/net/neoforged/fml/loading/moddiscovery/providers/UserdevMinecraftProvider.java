/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileProvider;
import net.neoforged.neoforgespi.locating.LoadResult;

public class UserdevMinecraftProvider implements IModFileProvider, ISystemModSource {
    @Override
    public List<LoadResult<IModFile>> provideModFiles(ILaunchContext launchContext) {
        // In dev, we have a joined distribution and both client and server will be in the same classes directory
        var classesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("net/minecraft/client/Minecraft.class");
        // resources will be in an extra jar
        var resourcesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("assets/.mcassetsroot");

        var mcJarContents = JarContents.of(Set.of(classesRoot, resourcesRoot));

        var mcJarMetadata = new ModJarMetadata(mcJarContents);
        var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
        var minecraftModFile = IModFile.create(mcSecureJar, this, MinecraftModInfo::buildMinecraftModInfo, null);
        mcJarMetadata.setModFile(minecraftModFile);

        return List.of(new LoadResult.Success<>(minecraftModFile));
    }

    @Override
    public String name() {
        return "minecraft (userdev)";
    }
}
