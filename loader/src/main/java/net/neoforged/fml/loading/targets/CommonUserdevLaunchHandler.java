/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.UserdevLocator;
import net.neoforged.fml.loading.moddiscovery.providers.DevEnvUtils;
import net.neoforged.fml.loading.moddiscovery.providers.NeoForgeDevProvider;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * For mod development environments.
 */
public abstract class CommonUserdevLaunchHandler extends CommonDevLaunchHandler {
    @Override
    public List<IModFileCandidateLocator> getAdditionalModFileLocators(VersionInfo versionInfo) {
        // Userdev is similar to neoforge dev with the only real difference being that the combined
        // output of the neoforge and patched mincraft sources are combined into a jar file
        var classesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("net/minecraft/client/Minecraft.class");

        return List.of(new NeoForgeDevProvider(List.of(classesRoot)), new UserdevLocator(getGroupedModFolders()));
    }
}
