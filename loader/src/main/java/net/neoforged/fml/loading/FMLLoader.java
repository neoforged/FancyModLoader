/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.LaunchServiceHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.ModuleLayerHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServicesHandler;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModValidator;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.fmlstartup.FatalStartupException;
import net.neoforged.fmlstartup.api.StartupArgs;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FMLLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static AccessTransformerEngine accessTransformer;
    private static LanguageProviderLoader languageProviderLoader;
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
    @Nullable
    private static ModuleLayer gameLayer;

    // This is called by FML Startup
    @SuppressWarnings("unused")
    public static void startup(StartupArgs startupArgs) {
        FMLPaths.loadAbsolutePaths(startupArgs.gameDirectory().toPath());
        FMLConfig.load();

        // Start up early display
        ImmediateWindowHandler.load(startupArgs.launchTarget(), startupArgs.programArgs());

        var instrumentation = startupArgs.instrumentation();
        // Make UnionFS work
        // TODO: This should come from a manifest? Service-Loader? Something...
        instrumentation.redefineModule(
                MethodHandle.class.getModule(),
                Set.of(),
                Map.of(),
                Map.of("java.lang.invoke", Set.of(SecureJar.class.getModule())),
                Set.of(),
                Map.of());

        // ML would usually handle these two arguments
        var moduleLayerHandler = new ModuleLayerHandler();
        var launchService = new LaunchServiceHandler(moduleLayerHandler);

        var transformStore = new TransformStore();
        var transformationServicesHandler = new TransformationServicesHandler(transformStore, moduleLayerHandler);
        var argumentHandler = new ArgumentHandler();
        var launchPlugins = new LaunchPluginHandler(moduleLayerHandler);

        var environment = new Environment(
                launchPlugins,
                launchService,
                moduleLayerHandler
        );
        Launcher launcher = new Launcher(
                transformationServicesHandler,
                environment,
                transformStore,
                argumentHandler,
                launchService,
                launchPlugins,
                moduleLayerHandler
        );
        Launcher.INSTANCE = launcher;

        var discoveryData = argumentHandler.setArgs(startupArgs.programArgs());
        transformationServicesHandler.discoverServices(discoveryData);
        final var scanResults = transformationServicesHandler.initializeTransformationServices(argumentHandler, environment)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        scanResults.getOrDefault(IModuleLayerManager.Layer.PLUGIN, List.of())
                .stream()
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .forEach(np-> moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.PLUGIN, np));
        moduleLayerHandler.buildLayer(IModuleLayerManager.Layer.PLUGIN);
        final var gameResults = transformationServicesHandler.triggerScanCompletion(moduleLayerHandler)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        final var gameContents = Stream.of(scanResults, gameResults)
                .flatMap(m -> m.getOrDefault(IModuleLayerManager.Layer.GAME, List.of()).stream())
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .toList();
        gameContents.forEach(j->moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.GAME, j));
        transformationServicesHandler.initialiseServiceTransformers();
        launchPlugins.offerScanResultsToPlugins(gameContents);
        // We do not do this: launchService.validateLaunchTarget(argumentHandler);
        var classLoader = transformationServicesHandler.buildTransformingClassLoader(launchPlugins, environment, moduleLayerHandler);
        Thread.currentThread().setContextClassLoader(classLoader);
        launchService.launch(argumentHandler, moduleLayerHandler.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow(), classLoader, launchPlugins);

        // ML would usually handle these two arguments
        environment.computePropertyIfAbsent(IEnvironment.Keys.MLSPEC_VERSION.get(), s -> IEnvironment.class.getPackage().getSpecificationVersion());
        environment.computePropertyIfAbsent(IEnvironment.Keys.MLIMPL_VERSION.get(), s -> IEnvironment.class.getPackage().getImplementationVersion());
        environment.computePropertyIfAbsent(IEnvironment.Keys.MODLIST.get(), s -> new ArrayList<>());
        environment.computePropertyIfAbsent(IEnvironment.Keys.GAMEDIR.get(), ignored -> startupArgs.gameDirectory().toPath());
        environment.computePropertyIfAbsent(IEnvironment.Keys.LAUNCHTARGET.get(), ignored -> startupArgs.launchTarget());

        moduleLayerHandler.buildLayer(IModuleLayerManager.Layer.SERVICE);

        try {
            FMLLoader.onInitialLoad(environment);
        } catch (IncompatibleEnvironmentException e) {
            throw new FatalStartupException(e.toString());
        }

        // Determine which Minecraft we're launching with
        try {
            var versionJsons = FMLLoader.class.getClassLoader().getResources("version.json");
            while (versionJsons.hasMoreElements()) {
                var versionJsonUri = versionJsons.nextElement();
                System.out.println(versionJsonUri);
            }
        } catch (IOException e) {
            throw new FatalStartupException(e.toString());
        }

//        serviceProvider.argumentValues(new ITransformationService.OptionResult() {
//            @Override
//            public <V> V value(OptionSpec<V> options) {
//                return result.valueOf(options);
//            }
//
//            @Override
//            public <V> List<V> values(OptionSpec<V> options) {
//                return result.valuesOf(options);
//            }
//        });
//
//        serviceProvider.initialize(environment);
//
//        // We need to redirect the launch context to add services reachable via the system classloader since
//        // this unit test and the main code is not loaded in a modular fashion
//        assertThat(serviceProvider.launchContext).isNotNull();
//        assertSame(environment, serviceProvider.launchContext.environment());
//        serviceProvider.launchContext = new TestLaunchContext(serviceProvider.launchContext, locatedPaths);
//
//        var pluginResources = serviceProvider.beginScanning(environment);
//        // In this phase, FML should only return plugin libraries
//        assertThat(pluginResources).extracting(ITransformationService.Resource::target).containsOnly(IModuleLayerManager.Layer.PLUGIN);
//        createModuleLayer(IModuleLayerManager.Layer.PLUGIN, pluginResources.stream().flatMap(resource -> resource.resources().stream()).toList());
//
//        var gameLayerResources = serviceProvider.completeScan(moduleLayerManager);
//        // In this phase, FML should only return game layer content
//        assertThat(gameLayerResources).extracting(ITransformationService.Resource::target).containsOnly(IModuleLayerManager.Layer.GAME);
//
//        // Query transformers now, which ML does before building the transforming class loader and launching the game
//        var transformers = serviceProvider.transformers();
//
//        var loadingModList = LoadingModList.get();
//        var loadedMods = loadingModList.getModFiles();
//
//        var pluginSecureJars = pluginResources.stream()
//                .flatMap(r -> r.resources().stream())
//                .collect(Collectors.toMap(
//                        SecureJar::name,
//                        Function.identity()));
//        var gameSecureJars = gameLayerResources.stream()
//                .flatMap(r -> r.resources().stream())
//                .collect(Collectors.toMap(
//                        SecureJar::name,
//                        Function.identity()));
//
//        // Wait for background scans of all mods to complete
//        for (var modFile : loadingModList.getModFiles()) {
//            modFile.getFile().getScanResult();
//        }
//
//        return new LaunchResult(
//                pluginSecureJars,
//                gameSecureJars,
//                loadingModList.getModLoadingIssues(),
//                loadedMods.stream().collect(Collectors.toMap(
//                        o -> o.getMods().getFirst().getModId(),
//                        o -> o)),
//                (List<ITransformer<?>>) transformers);
    }

    static void onInitialLoad(IEnvironment environment) throws IncompatibleEnvironmentException {
        final String version = LauncherVersion.getVersion();
        LOGGER.debug(LogMarkers.CORE, "FML {} loading", version);
        LOGGER.debug(LogMarkers.CORE, "FML found ModLauncher version : {}", environment.getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElse("unknown"));

        accessTransformer = ((AccessTransformerService) environment.findLaunchPlugin("accesstransformer").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "Access Transformer library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing AccessTransformer, cannot run");
        })).engine;

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

        runtimeDistCleaner.setDistribution(dist);
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
        FMLLoader.gameLayer = gameLayer;
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

    public static ModuleLayer getGameLayer() {
        if (gameLayer == null) {
            throw new IllegalStateException("This can only be called after mod discovery is completed");
        }
        return gameLayer;
    }

    public static VersionInfo versionInfo() {
        return versionInfo;
    }
}
