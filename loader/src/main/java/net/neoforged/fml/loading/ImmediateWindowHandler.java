/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class ImmediateWindowHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @Nullable
    static ImmediateWindowProvider provider;

    public static void load(final String launchTarget, final String[] arguments) {
        final var layer = Launcher.INSTANCE.findLayerManager()
                .flatMap(manager -> manager.getLayer(Layer.SERVICE))
                .orElseThrow(() -> new IllegalStateException("Couldn't find SERVICE layer"));
        ServiceLoader.load(layer, GraphicsBootstrapper.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(bootstrap -> {
                    LOGGER.debug("Invoking bootstrap method {}", bootstrap.name());
                    bootstrap.bootstrap(arguments);
                });
        if (!List.of("neoforgeclient", "neoforgeclientdev").contains(launchTarget)) {
            provider = null;
            LOGGER.info("ImmediateWindowProvider not loading because launch target is {}", launchTarget);
        } else if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            provider = null;
            LOGGER.info("ImmediateWindowProvider not loading because splash screen is disabled");
        } else {
            final var providername = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
            LOGGER.info("Loading ImmediateWindowProvider {}", providername);
            final var maybeProvider = ServiceLoader.load(layer, ImmediateWindowProvider.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(p -> Objects.equals(p.name(), providername))
                    .findFirst();
            provider = maybeProvider.or(() -> {
                LOGGER.info("Failed to find ImmediateWindowProvider {}, disabling", providername);
                return Optional.empty();
            }).orElse(null);
        }
        // Only update config if the provider isn't the dummy provider
        if (provider != null) {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, provider.name());
            FMLLoader.progressWindowTick = provider.initialize(arguments);
        } else {
            FMLLoader.progressWindowTick = () -> {};
        }
    }

    public static void acceptGameLayer(final ModuleLayer layer) {
        if (provider != null) {
            provider.updateModuleReads(layer);
        }
    }

    public static void updateProgress(final String message) {
        if (provider != null) {
            provider.updateProgress(message);
        }
    }

    public static void crash(final String message) {
        if (provider != null) {
            provider.crash(message);
        }
    }
}
