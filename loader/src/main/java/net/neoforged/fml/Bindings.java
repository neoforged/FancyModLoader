/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import java.util.ServiceLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigEvent;
import net.neoforged.fml.loading.FMLLoader;

public class Bindings {
    private static final IBindingsProvider provider;

    static {
        final var providers = ServiceLoaderUtils.streamServiceLoader(() -> ServiceLoader.load(FMLLoader.getGameLayer(), IBindingsProvider.class), sce -> {}).toList();
        if (providers.size() != 1) {
            throw new IllegalStateException("Could not find bindings provider");
        }
        provider = providers.get(0);
    }

    public static IEventBus getNeoForgeBus() {
        return provider.getNeoForgeBus();
    }

    public static String parseMessage(String i18nMessage, Object... args) {
        return provider.parseMessage(i18nMessage, args);
    }

    public static String stripControlCodes(String toStrip) {
        return provider.stripControlCodes(toStrip);
    }

    public static IConfigEvent.ConfigConfig getConfigConfiguration() {
        return provider.getConfigConfiguration();
    }
}
