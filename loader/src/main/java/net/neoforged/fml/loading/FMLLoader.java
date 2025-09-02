/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.IBindingsProvider;
import net.neoforged.fml.common.asm.AccessTransformerService;
import net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModValidator;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevDistCleaner;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class FMLLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static AccessTransformerEngine accessTransformer;
    private static LanguageProviderLoader languageProviderLoader;
    private static Dist dist;
    private static LoadingModList loadingModList;
    private static NeoForgeDevDistCleaner neoForgeDevDistCleaner;
    private static Path gamePath;
    private static VersionInfo versionInfo;
    private static String launchHandlerName;
    private static CommonLaunchHandler commonLaunchHandler;
    public static Runnable progressWindowTick;
    private static ModValidator modValidator;
    public static BackgroundScanHandler backgroundScanHandler;
    private static boolean production;
    @Nullable
    private static ModuleLayer gameLayer;

    @Nullable
    static volatile IBindingsProvider bindings;

    static void onInitialLoad(IEnvironment environment) throws IncompatibleEnvironmentException {
        final String version = LauncherVersion.getVersion();
        LOGGER.debug(LogMarkers.CORE, "FML {} loading", version);
        LOGGER.debug(LogMarkers.CORE, "FML found ModLauncher version : {}", environment.getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElse("unknown"));

        accessTransformer = ((AccessTransformerService) environment.findLaunchPlugin("accesstransformer").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "Access Transformer library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing AccessTransformer, cannot run");
        })).engine;

        try {
            Class.forName("net.neoforged.bus.api.IEventBus", false, environment.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error(LogMarkers.CORE, "Event Bus library is missing, we need this to run");
            throw new IncompatibleEnvironmentException("Missing EventBus, cannot run");
        }

        neoForgeDevDistCleaner = (NeoForgeDevDistCleaner) environment.findLaunchPlugin("neoforgedevdistcleaner").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "NeoForgeDevDistCleaner is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing NeoForgeDevDistCleaner, cannot run!");
        });
        LOGGER.debug(LogMarkers.CORE, "Found NeoForgeDev Dist Cleaner");

        try {
            Class.forName("com.electronwill.nightconfig.core.Config", false, environment.getClass().getClassLoader());
            Class.forName("com.electronwill.nightconfig.toml.TomlFormat", false, environment.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error(LogMarkers.CORE, "Failed to load NightConfig");
            throw new IncompatibleEnvironmentException("Missing NightConfig");
        }
    }

    static void setupLaunchHandler(IEnvironment environment, VersionInfo versionInfo) {
        var launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("MISSING");
        final Optional<ILaunchHandlerService> launchHandler = environment.findLaunchHandler(launchTarget);
        LOGGER.debug(LogMarkers.CORE, "Using {} as launch service", launchTarget);
        if (launchHandler.isEmpty()) {
            LOGGER.error(LogMarkers.CORE, "Missing LaunchHandler {}, cannot continue", launchTarget);
            throw new RuntimeException("Missing launch handler: " + launchTarget);
        }

        if (!(launchHandler.get() instanceof CommonLaunchHandler)) {
            LOGGER.error(LogMarkers.CORE, "Incompatible Launch handler found - type {}, cannot continue", launchHandler.get().getClass().getName());
            throw new RuntimeException("Incompatible launch handler found");
        }
        commonLaunchHandler = (CommonLaunchHandler) launchHandler.get();
        launchHandlerName = launchHandler.get().name();
        gamePath = environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(Paths.get(".").toAbsolutePath());
        FMLLoader.versionInfo = versionInfo;

        dist = commonLaunchHandler.getDist();
        production = commonLaunchHandler.isProduction();
        neoForgeDevDistCleaner.setDistribution(dist);
    }

    public static List<ITransformationService.Resource> beginModScan(ILaunchContext launchContext) {
        var additionalLocators = new ArrayList<IModFileCandidateLocator>();
        commonLaunchHandler.collectAdditionalModFileLocators(versionInfo, additionalLocators::add);

        var modDiscoverer = new ModDiscoverer(launchContext, additionalLocators);
        modValidator = modDiscoverer.discoverMods();
        var pluginResources = modValidator.getPluginResources();
        return List.of(pluginResources);
    }

    public static List<ITransformationService.Resource> completeScan(ILaunchContext launchContext, List<String> extraMixinConfigs) {
        languageProviderLoader = new LanguageProviderLoader(launchContext);
        backgroundScanHandler = modValidator.stage2Validation();
        loadingModList = backgroundScanHandler.getLoadingModList();
        if (!loadingModList.hasErrors()) {
            // Add extra mixin configs
            extraMixinConfigs.forEach(DeferredMixinConfigRegistration::addMixinConfig);
        }
        return List.of(modValidator.getModResources());
    }

    public static LanguageProviderLoader getLanguageLoadingProvider() {
        return languageProviderLoader;
    }

    public static void addAccessTransformer(String atPath, ModFile sourceModFile) {
        LOGGER.debug(LogMarkers.SCAN, "Adding Access Transformer in {}", sourceModFile.getFilePath());
        var resource = sourceModFile.getContents().get(atPath);
        if (resource == null) {
            LOGGER.error("AT {} is missing in {}", atPath, sourceModFile);
            return;
        }
        try (var reader = resource.bufferedReader()) {
            accessTransformer.loadAT(reader, atPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AT " + atPath + " from " + sourceModFile, e);
        }
    }

    public static Dist getDist() {
        return dist;
    }

    public static void beforeStart(ModuleLayer gameLayer) {
        FMLLoader.gameLayer = gameLayer;
        ImmediateWindowHandler.updateProgress("Launching minecraft");
        progressWindowTick.run();
    }

    public static LoadingModList getLoadingModList() {
        return loadingModList;
    }

    public static Path getGamePath() {
        return gamePath;
    }

    public static String getLauncherInfo() {
        return Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElse("MISSING");
    }

    public static List<Map<String, String>> modLauncherModList() {
        return Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MODLIST.get()).orElseGet(Collections::emptyList);
    }

    public static String launcherHandlerName() {
        return launchHandlerName;
    }

    public static boolean isProduction() {
        return production;
    }

    public static ModuleLayer getGameLayer() {
        if (gameLayer == null) {
            throw new IllegalStateException("This can only be called after mod discovery is completed");
        }
        return gameLayer;
    }

    public static VersionInfo versionInfo() {
        return versionInfo;
    }

    @ApiStatus.Internal
    public static IBindingsProvider getBindings() {
        if (bindings == null) {
            synchronized (FMLLoader.class) {
                if (bindings == null) {
                    var providers = ServiceLoader.load(FMLLoader.getGameLayer(), IBindingsProvider.class)
                            .stream().toList();
                    if (providers.size() != 1) {
                        throw new IllegalStateException("Could not find bindings provider");
                    }
                    bindings = providers.get(0).get();
                }
            }
        }
        return bindings;
    }
}
