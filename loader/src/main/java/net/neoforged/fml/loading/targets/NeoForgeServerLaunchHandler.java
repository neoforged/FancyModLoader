/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.ProductionProvider;
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

        output.accept(new ProductionProvider(
                new MavenCoordinate("net.neoforged", "minecraft-server-patched", "", "", versionInfo.neoForgeVersion()),
                new MavenCoordinate("net.neoforged", "neoforge", "", "universal", versionInfo.neoForgeVersion())));
    }
}
