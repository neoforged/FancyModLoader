/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.providers.NeoForgeUserDevLocator;
import net.neoforged.fml.loading.moddiscovery.providers.UserdevMinecraftProvider;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IModFileProvider;

/**
 * For mod development environments.
 */
public abstract class CommonUserdevLaunchHandler extends CommonDevLaunchHandler {
    @Override
    public List<IModFileProvider> getAdditionalModFileProviders(VersionInfo versionInfo) {
        return List.of(new UserdevMinecraftProvider());
    }

    @Override
    public List<IModFileCandidateLocator> getAdditionalModFileLocators(VersionInfo versionInfo) {
        var locators = new ArrayList<>(super.getAdditionalModFileLocators(versionInfo));
        locators.add(new NeoForgeUserDevLocator());
        return locators;
    }
}
