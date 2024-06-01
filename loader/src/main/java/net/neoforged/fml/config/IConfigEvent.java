/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.config;

import java.util.function.Function;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;

public interface IConfigEvent {
    record ConfigConfig(Function<ModConfig, IConfigEvent> loading, Function<ModConfig, IConfigEvent> reloading, @Nullable Function<ModConfig, IConfigEvent> unloading) {}

    ConfigConfig CONFIGCONFIG = FMLLoader.getBindings().getConfigConfiguration();

    static IConfigEvent reloading(ModConfig modConfig) {
        return CONFIGCONFIG.reloading().apply(modConfig);
    }

    static IConfigEvent loading(ModConfig modConfig) {
        return CONFIGCONFIG.loading().apply(modConfig);
    }

    @Nullable
    static IConfigEvent unloading(ModConfig modConfig) {
        return CONFIGCONFIG.unloading() == null ? null : CONFIGCONFIG.unloading().apply(modConfig);
    }

    ModConfig getConfig();

    default void post() {
        IEventBus bus = getConfig().container.getEventBus();

        if (bus != null) {
            bus.post(self());
        }
    }

    @SuppressWarnings("unchecked")
    default <T extends Event & IConfigEvent> T self() {
        return (T) this;
    }
}
