/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
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
import net.neoforged.fml.startup.FatalStartupException;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProviderFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ImmediateWindowHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String HANDOFF_CLASS = "net.neoforged.neoforge.client.loading.NoVizFallback";

    private static ImmediateWindowProvider provider;

    private static ProgressMeter earlyProgress;

    public static void load(boolean headless, ProgramArgs arguments) {
        earlyProgress = StartupNotificationManager.addProgressBar("EARLY", 0);
        earlyProgress.label("Bootstrapping Minecraft");

        if (headless) {
            provider = new HeadlessProvider();
            LOGGER.info("Not loading early display in headless mode.");
        } else {
            ServiceLoader.load(GraphicsBootstrapper.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .forEach(bootstrap -> {
                        LOGGER.debug("Invoking bootstrap method {}", bootstrap.name());
                        bootstrap.bootstrap(arguments.getArguments()); // TODO: Should take ProgramArgs
                    });
            if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
                provider = new DummyProvider();
                LOGGER.info("ImmediateWindowProvider not loading because splash screen is disabled");
            } else {
                final var providername = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
                LOGGER.info("Loading ImmediateWindowProvider {}", providername);
                final var maybeProvider = ServiceLoader.load(ImmediateWindowProviderFactory.class)
                        .stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(p -> Objects.equals(p.name(), providername))
                        .findFirst();
                provider = maybeProvider
                        .map(factory -> factory.create(arguments))
                        .orElseGet(() -> {
                            LOGGER.info("Failed to find ImmediateWindowProvider {}, disabling", providername);
                            return new DummyProvider();
                        });
            }
        }

        FMLLoader.progressWindowTick = provider::periodicTick;
    }

    public static long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor) {
        return provider.setupMinecraftWindow(width, height, title, monitor);
    }

    public static boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        return provider.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    public static void updateFBSize(IntConsumer width, IntConsumer height) {
        provider.updateFramebufferSize(width, height);
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

    private static final class DummyProvider implements ImmediateWindowProvider {
        private static Method NV_HANDOFF;
        private static Method NV_POSITION;
        private static Method NV_OVERLAY;
        private static Method NV_VERSION;

        @Override
        public void updateFramebufferSize(final IntConsumer width, final IntConsumer height) {}

        @Override
        public long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor) {
            try {
                var longsupplier = (LongSupplier) NV_HANDOFF.invoke(null, width, height, title, monitor);
                return longsupplier.getAsLong();
            } catch (Throwable e) {
                throw new IllegalStateException("How did you get here?", e);
            }
        }

        public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
            try {
                return (boolean) NV_POSITION.invoke(null, monitor, widthSetter, heightSetter, xSetter, ySetter);
            } catch (Throwable e) {
                throw new IllegalStateException("How did you get here?", e);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            try {
                return (Supplier<T>) NV_OVERLAY.invoke(null, mc, ri, ex, fade);
            } catch (Throwable e) {
                throw new IllegalStateException("How did you get here?", e);
            }
        }

        @Override
        public String getGLVersion() {
            try {
                return (String) NV_VERSION.invoke(null);
            } catch (Throwable e) {
                return "3.2"; // Vanilla sets 3.2 in com.mojang.blaze3d.platform.Window
            }
        }

        @Override
        public void updateModuleReads(ModuleLayer layer) {
            var nfModule = layer.findModule("neoforge").orElse(null);
            if (nfModule != null) {
                getClass().getModule().addReads(nfModule);
                var clz = Class.forName(nfModule, HANDOFF_CLASS);
                if (clz != null) {
                    var methods = Arrays.stream(clz.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())).collect(Collectors.toMap(Method::getName, Function.identity()));
                    NV_HANDOFF = methods.get("windowHandoff");
                    NV_OVERLAY = methods.get("loadingOverlay");
                    NV_POSITION = methods.get("windowPositioning");
                    NV_VERSION = methods.get("glVersion");
                } else {
                    LOGGER.error("Cannot hand over Minecraft window to NeoForge, since class {} wasn't found in {}.", HANDOFF_CLASS, nfModule);
                }
            } else {
                LOGGER.error("Cannot hand over Minecraft window to NeoForge, since module 'neoforge' wasn't found in {}.", layer);
            }
        }

        @Override
        public void periodicTick() {
            // NOOP
        }

        @Override
        public void crash(final String message) {
            // NOOP for unsupported environments
        }
    }

    private static final class HeadlessProvider implements ImmediateWindowProvider {
        @Override
        public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateModuleReads(ModuleLayer layer) {}

        @Override
        public void periodicTick() {}

        @Override
        public String getGLVersion() {
            return "0";
        }

        @Override
        public void crash(String message) {
            throw new FatalStartupException(message);
        }
    }
}
