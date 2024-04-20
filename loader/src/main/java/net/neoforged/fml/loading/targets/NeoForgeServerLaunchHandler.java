/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.PathBasedLocator;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

public class NeoForgeServerLaunchHandler extends CommonServerLaunchHandler {
    @Override
    public String name() {
        return "forgeserver";
    }

    /**
     * Overlays the unpatched but renamed Minecraft classes with our patched versions of those classes.
     */
    @Override
    protected List<MavenCoordinate> getAdditionalMinecraftJarContent(VersionInfo versionInfo) {
        return List.of(new MavenCoordinate("net.neoforged", "neoforge", "", "server", versionInfo.neoForgeVersion()));
    }

    @Override
    public List<IModFileCandidateLocator> getAdditionalModFileLocators(VersionInfo versionInfo) {
        var nfJar = LibraryFinder.findPathForMaven("net.neoforged", "neoforge", "", "universal", versionInfo.neoForgeVersion());
        return List.of(new PathBasedLocator("neoforge", nfJar));
    }
}