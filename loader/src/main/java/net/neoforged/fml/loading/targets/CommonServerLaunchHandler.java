/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.ProductionServerProvider;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * For production dedicated server environments.
 */
public abstract class CommonServerLaunchHandler extends CommonLaunchHandler {
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

    /**
     * @return Additional artifacts from the Games libraries folder that should be layered on top of the Minecraft jar content.
     */
    protected List<MavenCoordinate> getAdditionalMinecraftJarContent(VersionInfo versionInfo) {
        return List.of();
    }

    @Override
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        super.collectAdditionalModFileLocators(versionInfo, output);
        var additionalContent = getAdditionalMinecraftJarContent(versionInfo);
        output.accept(new ProductionServerProvider(additionalContent));
    }
}
