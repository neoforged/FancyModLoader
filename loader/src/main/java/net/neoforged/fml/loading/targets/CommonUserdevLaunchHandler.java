/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import java.util.function.Consumer;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevProvider;
import net.neoforged.fml.loading.moddiscovery.locators.UserdevLocator;
import net.neoforged.fml.util.DevEnvUtils;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * For mod development environments.
 */
public abstract class CommonUserdevLaunchHandler extends CommonDevLaunchHandler {
    @Override
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        // Userdev is similar to neoforge dev with the only real difference being that the combined
        // output of the neoforge and patched mincraft sources are combined into a jar file
        var classesRoot = DevEnvUtils.findFileSystemRootOfFileOnClasspath("net/minecraft/client/Minecraft.class");

        output.accept(new NeoForgeDevProvider(List.of(classesRoot)));
        output.accept(new UserdevLocator(getGroupedModFolders()));
    }
}
