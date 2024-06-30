/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.google.gson.Gson;
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
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModValidator;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeClientUserdevLaunchHandler;
import net.neoforged.fmlstartup.FatalStartupException;
import net.neoforged.fmlstartup.api.StartupArgs;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.neoforged.fml.loading.LogMarkers.CORE;

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
    public static void startup(Instrumentation instrumentation, StartupArgs startupArgs) {
        LOGGER.info("Starting FancyModLoader version {}", JarVersionLookupHandler.getVersion(FMLLoader.class).orElse("UNKNOWN"));

        // Make UnionFS work
        // TODO: This should come from a manifest? Service-Loader? Something...
        instrumentation.redefineModule(
                MethodHandle.class.getModule(),
                Set.of(),
                Map.of(),
                Map.of("java.lang.invoke", Set.of(SecureJar.class.getModule())),
                Set.of(),
                Map.of());

        var moduleLayerHandler = new ModuleLayerHandler();
        var launchPlugins = new HashMap<String, ILaunchPluginService>();
        var launchHandlers = new HashMap<String, ILaunchHandlerService>();
        var environment = new Environment(
                s -> Optional.ofNullable(launchPlugins.get(s)),
                s -> Optional.ofNullable(launchHandlers.get(s)),
                moduleLayerHandler);

        parseArgs(startupArgs.programArgs());

        var launchContext = new LaunchContext(
                environment,
                startupArgs.gameDirectory().toPath(),
                moduleLayerHandler,
                List.of(), // TODO: Argparse
                List.of(), // TODO: Argparse
                List.of() // TODO: Argparse
        );

        FMLPaths.loadAbsolutePaths(startupArgs.gameDirectory().toPath());
        FMLConfig.load();

        ImmediateWindowHandler.load(startupArgs.launchTarget(), startupArgs.programArgs());

        LOGGER.debug(CORE, "Preparing launch handler");
        var launchHandler = switch (startupArgs.launchTarget()) {
            case "" -> new NeoForgeClientUserdevLaunchHandler();
            default -> {
                LOGGER.error("Unknown launch target. Defaulting to start the client.");
                yield new NeoForgeClientUserdevLaunchHandler();
            }
        };

        FMLLoader.setupLaunchHandler(
                new VersionInfo("", "", "", ""),
                launchHandler);
        // Only register the one we already know is selected
        launchHandlers.put(commonLaunchHandler.name(), commonLaunchHandler);
        FMLEnvironment.setupInteropEnvironment(environment);
        net.neoforged.neoforgespi.Environment.build(environment);

        var discoveryResult = runOffThread(() -> runDiscovery(launchContext));

        // TODO: There is no reason to defer this since we have much more control over startup now
        if (!loadingModList.hasErrors()) {
            // Add extra mixin configs
            var extraMixinConfigs = System.getProperty("fml.extraMixinConfigs");
            if (extraMixinConfigs != null) {
                for (String s : extraMixinConfigs.split(File.pathSeparator)) {
                    DeferredMixinConfigRegistration.addMixinConfig(s);
                }
            }
        }

        // ML would usually handle these two arguments
        var launchService = new LaunchServiceHandler(Stream.empty());

        var transformStore = new TransformStore();
        var transformationServicesHandler = new TransformationServicesHandler(transformStore, moduleLayerHandler);
        var argumentHandler = new ArgumentHandler();

        // Add our own launch plugins explicitly
        accessTransformer = addLaunchPlugin(launchPlugins, new AccessTransformerService()).engine;
        addLaunchPlugin(launchPlugins, new RuntimeEnumExtender());
        runtimeDistCleaner = addLaunchPlugin(launchPlugins, new RuntimeDistCleaner(commonLaunchHandler.getDist()));

        var launchPluginHandler = new LaunchPluginHandler(launchPlugins.values().stream());

        Launcher.INSTANCE = new Launcher(
                transformationServicesHandler,
                environment,
                transformStore,
                argumentHandler,
                launchService,
                launchPluginHandler,
                moduleLayerHandler);

        argumentHandler.setArgs(startupArgs.programArgs());
        transformationServicesHandler.discoverServices(new ArgumentHandler.DiscoveryData(
                startupArgs.gameDirectory().toPath(), startupArgs.launchTarget(), startupArgs.programArgs()
        ));

        // ITransformationService.class.getName(),
        // IModFileCandidateLocator.class.getName(),
        // IModFileReader.class.getName(),
        // IDependencyLocator.class.getName(),
        // TODO -> MANIFEST.MF declaration GraphicsBootstrapper.class.getName(),
        // TODO -> MANIFEST.MF declaration net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider.class.getName()

        final var scanResults = transformationServicesHandler.initializeTransformationServices(argumentHandler, environment)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        scanResults.getOrDefault(IModuleLayerManager.Layer.PLUGIN, List.of())
                .stream()
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .forEach(np -> moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.PLUGIN, np));
        moduleLayerHandler.buildLayer(IModuleLayerManager.Layer.PLUGIN);
        final var gameResults = transformationServicesHandler.triggerScanCompletion(moduleLayerHandler)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        final var gameContents = Stream.of(scanResults, gameResults)
                .flatMap(m -> m.getOrDefault(IModuleLayerManager.Layer.GAME, List.of()).stream())
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .toList();
        gameContents.forEach(j -> moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.GAME, j));
        transformationServicesHandler.initialiseServiceTransformers();
        launchPluginHandler.offerScanResultsToPlugins(gameContents);
        // We do not do this: launchService.validateLaunchTarget(argumentHandler);
        var classLoader = transformationServicesHandler.buildTransformingClassLoader(launchPluginHandler, environment, moduleLayerHandler);
        // From here on out, try loading through the TCL
        Thread.currentThread().setContextClassLoader(classLoader);

        var gameLayer = moduleLayerHandler.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow();
        commonLaunchHandler.launchService(startupArgs.programArgs(), gameLayer);

        throw new FatalStartupException("x");
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

    private static <T extends ILaunchPluginService> T addLaunchPlugin(Map<String, ILaunchPluginService> services,
                                                                      T service) {
        LOGGER.debug("Adding built-in launch plugin {}", service.name());
        services.put(service.name(), service);
        return service;
    }

    private static void parseArgs(String[] strings) {
        String neoForgeVersion = null;
        String mcVersion = null;
        String neoFormVersion = null;

        for (int i = 0; i < strings.length; i++) {
            var arg = strings[i];

            String option;
            if (arg.startsWith("--")) {
                option = arg.substring(2);
            } else if (arg.startsWith("-")) {
                option = arg.substring(1);
            } else {
                continue; // Unknown option
            }

            if (i + 1 < strings.length) {
                switch (option) {
                    case "neoForgeVersion" -> neoForgeVersion = strings[++i];
                    case "fmlVersion" -> ++i;
                    case "mcVersion" -> mcVersion = strings[++i];
                    case "neoFormVersion" -> neoFormVersion = strings[++i];
                }
            }
        }
    }

    record DiscoveryResult(List<ModFile> pluginContent, List<ModFile> gameContent) {
    }

    private static DiscoveryResult runDiscovery(ILaunchContext launchContext) {
        var progress = StartupNotificationManager.prependProgressBar("Discovering mods...", 0);

        var additionalLocators = new ArrayList<IModFileCandidateLocator>();
        commonLaunchHandler.collectAdditionalModFileLocators(versionInfo, additionalLocators::add);

        var modDiscoverer = new ModDiscoverer(launchContext, additionalLocators);
        modValidator = modDiscoverer.discoverMods();
        var pluginResources = modValidator.getPluginResources();

        languageProviderLoader = new LanguageProviderLoader(launchContext);
        backgroundScanHandler = modValidator.stage2Validation();
        loadingModList = backgroundScanHandler.getLoadingModList();

        var gameResources = modValidator.getModResources();

        progress.complete();

        return new DiscoveryResult(
                pluginResources,
                gameResources);
    }

    private static <T> T runOffThread(Supplier<T> supplier) {
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Throwable e) {
                LOGGER.error("Off-thread operation failed.", e);
                throw new CompletionException(e);
            }
        });

        while (true) {
            ImmediateWindowHandler.renderTick();
            try {
                return future.get(10L, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for future", e);
            } catch (TimeoutException ignored) {
            }
        }
    }

    static void onInitialLoad(IEnvironment environment) throws IncompatibleEnvironmentException {
        final String version = LauncherVersion.getVersion();
        LOGGER.debug(LogMarkers.CORE, "FML {} loading", version);
        LOGGER.debug(LogMarkers.CORE, "FML found ModLauncher version : {}", environment.getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElse("unknown"));

        accessTransformer = ((AccessTransformerService) environment.findLaunchPlugin("accesstransformer").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "Access Transformer library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing AccessTransformer, cannot run");
        })).engine;

        runtimeDistCleaner = (RuntimeDistCleaner) environment.findLaunchPlugin("runtimedistcleaner").orElseThrow(() -> {
            LOGGER.error(LogMarkers.CORE, "Dist Cleaner is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing DistCleaner, cannot run!");
        });
        LOGGER.debug(LogMarkers.CORE, "Found Runtime Dist Cleaner");

        try {
            LOGGER.debug(LogMarkers.CORE, "FML found EventBus version : {}", JarVersionLookupHandler.getVersion(IEventBus.class).orElse("UNKNOWN"));
        } catch (NoClassDefFoundError e) {
            LOGGER.error(LogMarkers.CORE, "Event Bus library is missing, we need this to run");
            throw new IncompatibleEnvironmentException("Missing EventBus, cannot run");
        }

        try {
            Class.forName("com.electronwill.nightconfig.core.Config", false, environment.getClass().getClassLoader());
            Class.forName("com.electronwill.nightconfig.toml.TomlFormat", false, environment.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error(LogMarkers.CORE, "Failed to load NightConfig");
            throw new IncompatibleEnvironmentException("Missing NightConfig");
        }
    }

    static void setupLaunchHandler(VersionInfo versionInfo, CommonLaunchHandler launchHandler) {
        commonLaunchHandler = launchHandler;
        launchHandlerName = launchHandler.name();
        gamePath = FMLPaths.GAMEDIR.get();
        FMLLoader.versionInfo = versionInfo;

        dist = commonLaunchHandler.getDist();
        production = commonLaunchHandler.isProduction();

        if (runtimeDistCleaner != null) {
            runtimeDistCleaner.setDistribution(dist);
        }
    }

    @Deprecated(forRemoval = true)
    public static List<ITransformationService.Resource> beginModScan(ILaunchContext launchContext) {
        var additionalLocators = new ArrayList<IModFileCandidateLocator>();
        commonLaunchHandler.collectAdditionalModFileLocators(versionInfo, additionalLocators::add);

        var modDiscoverer = new ModDiscoverer(launchContext, additionalLocators);
        modValidator = modDiscoverer.discoverMods();
        var pluginResources = modValidator.getPluginResources();
        return List.of(new ITransformationService.Resource(IModuleLayerManager.Layer.PLUGIN, pluginResources.stream().map(IModFile::getSecureJar).toList()));
    }

    @Deprecated(forRemoval = true)
    public static List<ITransformationService.Resource> completeScan(ILaunchContext launchContext, List<String> extraMixinConfigs) {
        languageProviderLoader = new LanguageProviderLoader(launchContext);
        backgroundScanHandler = modValidator.stage2Validation();
        loadingModList = backgroundScanHandler.getLoadingModList();
        if (!loadingModList.hasErrors()) {
            // Add extra mixin configs
            extraMixinConfigs.forEach(DeferredMixinConfigRegistration::addMixinConfig);
        }
        return List.of(
                new ITransformationService.Resource(IModuleLayerManager.Layer.GAME, modValidator.getModResources().stream().map(ModFile::getSecureJar).toList()));
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
