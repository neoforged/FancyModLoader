/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
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

    private static Optional<ImmediateWindowProvider> loadProviderByName(ILaunchContext context, String providerName) {
        return ServiceLoaderUtil.loadEarlyServices(context, ImmediateWindowProvider.class, List.of())
                .stream()
                .filter(p -> Objects.equals(p.name(), providerName))
                .findFirst();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean tryLoadProvider(ILaunchContext context, String providerName, ProgramArgs arguments) {
        LOGGER.info("Loading ImmediateWindowProvider {}", providerName);
        provider = loadProviderByName(context, providerName).orElse(null);

        if (provider == null) {
            LOGGER.warn("Failed to find ImmediateWindowProvider {}", providerName);
            return false;
        }

        if (!provider.isSupportedEnvironment()) {
            LOGGER.warn("ImmediateWindowProvider {} cannot run in the current environment", providerName);
            return false;
        }

        try {
            provider.initialize(arguments);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize ImmediateWindowProvider '{}'", providerName, e);
            return false;
        }

        return true;
    }

    public static void load(ILaunchContext context, boolean headless, ProgramArgs arguments) {
        if (headless) {
            provider = null;
            LOGGER.info("Not loading early display in headless mode.");
            return;
        }

        ServiceLoaderUtil.loadEarlyServices(context, GraphicsBootstrapper.class, List.of())
                .forEach(bootstrap -> {
                    LOGGER.info("Running graphics bootstrap plugin {}", bootstrap.name());
                    bootstrap.bootstrap(arguments);
                });

        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            provider = null;
            LOGGER.info("ImmediateWindowProvider not loading because splash screen is disabled");

            return;
        }

        final var providerName = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);

        if (!tryLoadProvider(context, providerName, arguments)) {
            final var defaultProviderName = FMLConfig.getDefaultConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);

            LOGGER.warn("Fallbacking to default ImmediateWindowProvider {}", defaultProviderName);
            if (providerName.equals(defaultProviderName) || !tryLoadProvider(context, defaultProviderName, arguments)) {
                LOGGER.warn("Failed loading default ImmediateWindowProvider {}, disabling", defaultProviderName);
                provider = null;
                return;
            }
        }

        assert provider != null;

        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, provider.name());
    }

    public static void setNeoForgeVersion(String version) {
        if (provider != null) {
            provider.setNeoForgeVersion(version);
        }
    }

    public static void setMinecraftVersion(String version) {
        if (provider != null) {
            provider.setMinecraftVersion(version);
        }
    }

    public static void renderTick() {
        if (provider != null) {
            provider.periodicTick();
        }
    }

    public static void updateProgress(String message) {
        if (provider != null) {
            provider.updateProgress(message);
        }
    }

    public static void crash(String message) {
        if (provider != null) {
            provider.crash(message);
        }
    }

    public static void displayFatalErrorAndExit(List<ModLoadingIssue> issues, @Nullable Path modsFolder, @Nullable Path logFile, @Nullable Path crashReportFile) {
        if (provider != null) {
            provider.displayFatalErrorAndExit(issues, modsFolder, logFile, crashReportFile);
        }
    }
}
