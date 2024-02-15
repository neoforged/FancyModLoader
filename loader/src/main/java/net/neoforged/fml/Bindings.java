/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigEvent;
import net.neoforged.fml.loading.FMLLoader;

public class Bindings {
    private static final Bindings INSTANCE = new Bindings();

    private final IBindingsProvider provider;

    private Bindings() {
        final var providers = ServiceLoaderUtils.streamServiceLoader(() -> ServiceLoader.load(FMLLoader.getGameLayer(), IBindingsProvider.class), sce -> {}).toList();
        if (providers.size() != 1) {
            throw new IllegalStateException("Could not find bindings provider");
        }
        this.provider = providers.get(0);
    }

    public static Supplier<IEventBus> getForgeBus() {
        return INSTANCE.provider.getForgeBusSupplier();
    }

    public static Supplier<I18NParser> getMessageParser() {
        return INSTANCE.provider.getMessageParser();
    }

    public static Supplier<IConfigEvent.ConfigConfig> getConfigConfiguration() {
        return INSTANCE.provider.getConfigConfiguration();
    }
}
