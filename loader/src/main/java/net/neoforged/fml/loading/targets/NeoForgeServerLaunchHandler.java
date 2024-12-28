/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.PathBasedLocator;
import net.neoforged.fml.loading.moddiscovery.locators.ProductionServerProvider;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * For production dedicated server environments.
 */
public class NeoForgeServerLaunchHandler extends CommonLaunchHandler {
    @Override
    public String name() {
        return "neoforgeserver";
    }

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
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        super.collectAdditionalModFileLocators(versionInfo, output);
        // Overlays the unpatched but renamed Minecraft classes with our patched versions of those classes.
        var additionalContent = List.of(new MavenCoordinate("net.neoforged", "neoforge", "", "server", versionInfo.neoForgeVersion()));
        output.accept(new ProductionServerProvider(additionalContent));

        var nfJar = LibraryFinder.findPathForMaven("net.neoforged", "neoforge", "", "universal", versionInfo.neoForgeVersion());
        output.accept(new PathBasedLocator("neoforge", nfJar));
    }
}
