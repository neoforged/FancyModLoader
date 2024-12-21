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
import net.neoforged.fml.loading.moddiscovery.locators.ProductionClientProvider;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * For production client environments (i.e. vanilla launcher).
 */
public class NeoForgeClientLaunchHandler extends CommonLaunchHandler {
    @Override
    public String name() {
        return "neoforgeclient";
    }

    @Override
    public Dist getDist() {
        return Dist.CLIENT;
    }

    @Override
    public boolean isProduction() {
        return true;
    }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        clientService(arguments, gameLayer);
    }

    @Override
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        super.collectAdditionalModFileLocators(versionInfo, output);

        // Overlays the unpatched but renamed Minecraft classes with our patched versions of those classes.
        var additionalContent = List.of(new MavenCoordinate("net.neoforged", "neoforge", "", "client", versionInfo.neoForgeVersion()));
        output.accept(new ProductionClientProvider(additionalContent));

        var nfJar = LibraryFinder.findPathForMaven("net.neoforged", "neoforge", "", "universal", versionInfo.neoForgeVersion());
        output.accept(new PathBasedLocator("neoforge", nfJar));
    }
}
