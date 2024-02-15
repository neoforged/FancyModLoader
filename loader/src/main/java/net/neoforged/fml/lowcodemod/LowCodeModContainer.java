/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.lowcodemod;

import static net.neoforged.fml.loading.LogMarkers.LOADING;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class LowCodeModContainer extends ModContainer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ModFileScanData scanResults;
    private Object modInstance;

    public LowCodeModContainer(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
        super(info);
        LOGGER.debug(LOADING, "Creating LowCodeModContainer for {}", info.getModId());
        this.scanResults = modFileScanResults;
        this.modInstance = new Object();
        this.contextExtension = () -> null;
        this.extensionPoints.remove(IExtensionPoint.DisplayTest.class);
    }

    @Override
    public boolean matches(Object mod) {
        return mod == modInstance;
    }

    @Override
    public Object getMod() {
        return modInstance;
    }

    @Override
    public @Nullable IEventBus getEventBus() {
        return null;
    }
}
