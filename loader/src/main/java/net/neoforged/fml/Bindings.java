/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigEvent;
import net.neoforged.fml.loading.FMLLoader;

import java.util.ServiceLoader;
import java.util.function.Supplier;

public class Bindings {
    private static Bindings instance;

    private final IBindingsProvider provider;

    private Bindings() {
        final var providers = ServiceLoaderUtils.streamServiceLoader(()->ServiceLoader.load(FMLLoader.getGameLayer(), IBindingsProvider.class), sce->{}).toList();
        if (providers.size() != 1) {
            throw new IllegalStateException("Could not find bindings provider");
        }
        this.provider = providers.get(0);
    }

    private static Bindings getInstance() {
        if (instance == null) {
            instance = new Bindings();
        }
        return instance;
    }


    public static Supplier<IEventBus> getForgeBus() {
        return getInstance().provider.getForgeBusSupplier();
    }

    public static Supplier<I18NParser> getMessageParser() {
        return getInstance().provider.getMessageParser();
    }

    public static Supplier<IConfigEvent.ConfigConfig> getConfigConfiguration() {
        return getInstance().provider.getConfigConfiguration();
    }
}
