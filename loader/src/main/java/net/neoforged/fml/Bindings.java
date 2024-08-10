/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.ServiceLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class Bindings {
    private static final IBindingsProvider provider;

    static {
        var providers = ServiceLoader.load(FMLLoader.getGameLayer(), IBindingsProvider.class)
                .stream().toList();
        if (providers.size() != 1) {
            throw new IllegalStateException("Could not find bindings provider");
        }
        provider = providers.get(0).get();
    }

    public static IEventBus getGameBus() {
        return provider.getGameBus();
    }

    public static void fireConfigChanged(ModConfig modConfig, @Nullable IConfigSpec.ILoadedConfig loadedConfig) {
        provider.onConfigChanged(modConfig, loadedConfig);
    }
}
