/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.coremod.CoreModScriptingEngine;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration;
import net.neoforged.fml.loading.moddiscovery.BackgroundScanHandler;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModValidator;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import org.slf4j.Logger;

public class FMLLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static AccessTransformerEngine accessTransformer;
    private static ModDiscoverer modDiscoverer;
    private static CoreModScriptingEngine coreModEngine;
    private static LanguageLoadingProvider languageLoadingProvider;
    private static Dist dist;
    private static LoadingModList loadingModList;
    private static RuntimeDistCleaner runtimeDistCleaner;
    private static Path gamePath;
    private static VersionInfo versionInfo;
    private static String launchHandlerName;
    private static CommonLaunchHandler commonLaunchHandler;
    public static Runnable progressWindowTick;
    private static ModValidator modValidator;
    public static BackgroundScanHandler backgroundScanHandler;
    private static boolean production;
    private static IModuleLayerManager moduleLayerManager;

    static void onInitialLoad(IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException {
        final String version = LauncherVersion.getVersion();
        LOGGER.debug(LogMarkers.CORE, "FML {} loading", version);
        final Package modLauncherPackage = ITransformationService.class.getPackage();
        LOGGER.debug(LogMarkers.CORE, "FML found ModLauncher version : {}", modLauncherPackage.getImplementationVersion());
        if (!modLauncherPackage.isCompatibleWith("4.0")) {
            LOGGER.error(LogMarkers.CORE, "Found incompatible ModLauncher specification : {}, version {} from {}", modLauncherPackage.getSpecificationVersion(), modLauncherPackage.getImplementationVersion(), modLauncherPackage.getImplementationVendor());
            throw new IncompatibleEnvironmentException("Incompatible modlauncher found " + modLauncherPackage.getSpecificationVersion());
        }

        accessTransformer = ((AccessTransformerService) environment.findLaunchPlugin("accesstransformer").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "Access Transformer library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing AccessTransformer, cannot run");
        })).engine;

        final Package atPackage = accessTransformer.getClass().getPackage();
        LOGGER.debug(LogMarkers.CORE, "FML found AccessTransformer version : {}", atPackage.getImplementationVersion());
        if (!atPackage.isCompatibleWith("1.0")) {
            LOGGER.error(LogMarkers.CORE, "Found incompatible AccessTransformer specification : {}, version {} from {}", atPackage.getSpecificationVersion(), atPackage.getImplementationVersion(), atPackage.getImplementationVendor());
            throw new IncompatibleEnvironmentException("Incompatible accesstransformer found " + atPackage.getSpecificationVersion());
        }

        try {
            var eventBus = Class.forName("net.neoforged.bus.api.IEventBus", false, environment.getClass().getClassLoader());
            LOGGER.debug(LogMarkers.CORE, "FML found EventBus version : {}", eventBus.getPackage().getImplementationVersion());
        } catch (ClassNotFoundException e) {
            LOGGER.error(LogMarkers.CORE, "Event Bus library is missing, we need this to run");
            throw new IncompatibleEnvironmentException("Missing EventBus, cannot run");
        }

        runtimeDistCleaner = (RuntimeDistCleaner) environment.findLaunchPlugin("runtimedistcleaner").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "Dist Cleaner is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing DistCleaner, cannot run!");
        });
        LOGGER.debug(LogMarkers.CORE, "Found Runtime Dist Cleaner");

        coreModEngine = new CoreModScriptingEngine();
        LOGGER.debug(LogMarkers.CORE, "FML found CoreMods version : {}", coreModEngine.getClass().getPackage().getImplementationVersion());

        try {
            Class.forName("com.electronwill.nightconfig.core.Config", false, environment.getClass().getClassLoader());
            Class.forName("com.electronwill.nightconfig.toml.TomlFormat", false, environment.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error(LogMarkers.CORE, "Failed to load NightConfig");
            throw new IncompatibleEnvironmentException("Missing NightConfig");
        }
    }

    static void setupLaunchHandler(final IEnvironment environment, final Map<String, Object> arguments) {
        final String launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("MISSING");
        arguments.put("launchTarget", launchTarget);
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

        dist = commonLaunchHandler.getDist();
        production = commonLaunchHandler.isProduction();

        versionInfo = new VersionInfo(arguments);

        LOGGER.debug(LogMarkers.CORE, "Received command line version data  : {}", versionInfo);

        runtimeDistCleaner.getExtension().accept(dist);
    }

    public static List<ITransformationService.Resource> beginModScan(final Map<String, ?> arguments) {
        LOGGER.debug(LogMarkers.SCAN, "Scanning for Mod Locators");
        modDiscoverer = new ModDiscoverer(arguments);
        modValidator = modDiscoverer.discoverMods();
        var pluginResources = modValidator.getPluginResources();
        return List.of(pluginResources);
    }

    public static List<ITransformationService.Resource> completeScan(IModuleLayerManager layerManager, List<String> extraMixinConfigs) {
        moduleLayerManager = layerManager;
        languageLoadingProvider = new LanguageLoadingProvider();
        backgroundScanHandler = modValidator.stage2Validation();
        loadingModList = backgroundScanHandler.getLoadingModList();
        if (loadingModList.getErrors().isEmpty()) {
            // Add extra mixin configs
            extraMixinConfigs.forEach(DeferredMixinConfigRegistration::addMixinConfig);
        }
        return List.of(modValidator.getModResources());
    }

    static CoreModScriptingEngine getCoreModEngine() {
        return coreModEngine;
    }

    public static LanguageLoadingProvider getLanguageLoadingProvider() {
        return languageLoadingProvider;
    }

    static ModDiscoverer getModDiscoverer() {
        return modDiscoverer;
    }

    public static CommonLaunchHandler getLaunchHandler() {
        return commonLaunchHandler;
    }

    public static void addAccessTransformer(Path atPath, ModFile modName) {
        LOGGER.debug(LogMarkers.SCAN, "Adding Access Transformer in {}", modName.getFilePath());
        try {
            accessTransformer.loadATFromPath(atPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AT at " + atPath.toAbsolutePath(), e);
        }
    }

    public static Dist getDist() {
        return dist;
    }

    public static void beforeStart(ModuleLayer gameLayer) {
        ImmediateWindowHandler.acceptGameLayer(gameLayer);
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

    public static boolean isSecureJarEnabled() {
        return true;
    }

    public static ModuleLayer getGameLayer() {
        return moduleLayerManager.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow();
    }

    public static VersionInfo versionInfo() {
        return versionInfo;
    }
}
