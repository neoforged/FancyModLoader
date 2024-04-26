/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import java.util.function.Consumer;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.PathBasedLocator;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

public class NeoForgeClientLaunchHandler extends CommonClientLaunchHandler {
    @Override
    public String name() {
        return "forgeclient";
    }

    /**
     * Overlays the unpatched but renamed Minecraft classes with our patched versions of those classes.
     */
    @Override
    protected List<MavenCoordinate> getAdditionalMinecraftJarContent(VersionInfo versionInfo) {
        return List.of(MavenCoordinate.parse("net.neoforged:neoforge:client:" + versionInfo.neoForgeVersion()));
    }

    @Override
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        super.collectAdditionalModFileLocators(versionInfo, output);
        var nfJar = LibraryFinder.findPathForMaven("net.neoforged", "neoforge", "", "universal", versionInfo.neoForgeVersion());
        output.accept(new PathBasedLocator("neoforge", nfJar));
    }
}
