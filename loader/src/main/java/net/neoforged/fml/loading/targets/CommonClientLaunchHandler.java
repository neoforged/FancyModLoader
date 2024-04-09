/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.providers.ProductionClientProvider;
import net.neoforged.neoforgespi.locating.IModFileProvider;

/**
 * For production client environments (i.e. vanilla launcher).
 */
public abstract class CommonClientLaunchHandler extends CommonLaunchHandler {
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

    /**
     * @return Additional artifacts from the Games libraries folder that should be layered on top of the Minecraft jar content.
     */
    protected List<MavenCoordinate> getAdditionalMinecraftJarContent(VersionInfo versionInfo) {
        return List.of();
    }

    @Override
    public List<IModFileProvider> getAdditionalModFileProviders(VersionInfo versionInfo) {
        var additionalContent = getAdditionalMinecraftJarContent(versionInfo);
        return List.of(new ProductionClientProvider(additionalContent));
    }
}
