/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
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

    private static ProgressMeter earlyProgress;

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
        earlyProgress = StartupNotificationManager.addProgressBar("EARLY", 0);
        earlyProgress.label("Bootstrapping Minecraft");
    }

    public static <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        earlyProgress.complete();
        return provider.loadingOverlay(mc, ri, ex, fade);
    }

    public static void acceptGameLayer(final ModuleLayer layer) {
        provider.updateModuleReads(layer);
    }

    public static void renderTick() {
        provider.periodicTick();
    }

    public static String getGLVersion() {
        return provider.getGLVersion();
    }

    public static void updateProgress(final String message) {
        earlyProgress.label(message);
    }

    public static void crash(final String message) {
        provider.crash(message);
    }
}
