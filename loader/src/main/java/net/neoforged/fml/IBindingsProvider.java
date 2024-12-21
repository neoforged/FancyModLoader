/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IBindingsProvider {
    IEventBus getGameBus();

    /**
     * Invoked when a config changed or might have changed, but not necessarily when a config is loaded.
     * This method may be called from any thread, at any time.
     */
    void onConfigChanged(ModConfig modConfig, @Nullable IConfigSpec.ILoadedConfig loadedConfig);
}
