/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.DiscoveryData;
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
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
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
import java.util.Objects;
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
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModValidator;
import net.neoforged.fml.loading.moddiscovery.locators.ClasspathLibrariesLocator;
import net.neoforged.fml.loading.moddiscovery.locators.GameLocator;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeClientUserdevLaunchHandler;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.fmlstartup.api.StartupArgs;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;

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

        var externalOptions = parseArgs(startupArgs.programArgs());

        var launchContext = new LaunchContext(
                environment,
                startupArgs.gameDirectory().toPath(),
                moduleLayerHandler,
                List.of(), // TODO: Argparse
                List.of(), // TODO: Argparse
                List.of() // TODO: Argparse
        );

        for (var claimedFile : startupArgs.claimedFiles()) {
            launchContext.addLocated(claimedFile.toPath());
        }

        gamePath = startupArgs.gameDirectory().toPath();
        FMLPaths.loadAbsolutePaths(gamePath);
        FMLConfig.load();

        ImmediateWindowHandler.load(startupArgs.launchTarget(), startupArgs.programArgs());

        LOGGER.debug(CORE, "Preparing launch handler");
        commonLaunchHandler = switch (startupArgs.launchTarget()) {
            case "forgeclientuserdev" -> new NeoForgeClientUserdevLaunchHandler();
            default -> {
                LOGGER.error("Unknown launch target. Defaulting to start the client.");
                yield new NeoForgeClientUserdevLaunchHandler();
            }
        };

        launchHandlerName = commonLaunchHandler.name();
        versionInfo = new VersionInfo(
                externalOptions.neoForgeVersion(),
                externalOptions.fmlVersion(),
                externalOptions.mcVersion(),
                externalOptions.neoFormVersion());

        // TODO: These should be determine via ambient information too
        dist = commonLaunchHandler.getDist();
        production = commonLaunchHandler.isProduction();

        // Only register the one we already know is selected
        launchHandlers.put(commonLaunchHandler.name(), commonLaunchHandler);
        FMLEnvironment.setupInteropEnvironment(environment);
        net.neoforged.neoforgespi.Environment.build(environment);

        // Add our own launch plugins explicitly. These do need to exist before mod discovery,
        // as mod discovery will add its results to these engines directly.
        accessTransformer = addLaunchPlugin(launchPlugins, new AccessTransformerService()).engine;
        addLaunchPlugin(launchPlugins, new RuntimeEnumExtender());
        runtimeDistCleaner = addLaunchPlugin(launchPlugins, new RuntimeDistCleaner(commonLaunchHandler.getDist()));

        var discoveryResult = runOffThread(() -> runDiscovery(launchContext));

        for (var issue : discoveryResult.discoveryIssues) {
            LOGGER.atLevel(issue.severity() == ModLoadingIssue.Severity.ERROR ? Level.ERROR : Level.WARN)
                    .setCause(issue.cause())
                    .log("{}", FMLTranslations.translateIssueEnglish(issue));
        }

        // TODO: There is no reason to defer this since we have much more control over startup now
        // Add extra mixin configs
        var extraMixinConfigs = System.getProperty("fml.extraMixinConfigs");
        if (extraMixinConfigs != null) {
            for (String s : extraMixinConfigs.split(File.pathSeparator)) {
                DeferredMixinConfigRegistration.addMixinConfig(s);
            }
        }

        // ML would usually handle these two arguments
        var launchService = new LaunchServiceHandler(Stream.empty());

        var transformStore = new TransformStore();
        var transformationServicesHandler = new TransformationServicesHandler(transformStore, moduleLayerHandler);
        var argumentHandler = new ArgumentHandler(startupArgs.programArgs());

        var launchPluginHandler = new LaunchPluginHandler(launchPlugins.values().stream());

        Launcher.INSTANCE = new Launcher(
                transformationServicesHandler,
                environment,
                transformStore,
                launchService,
                launchPluginHandler,
                moduleLayerHandler);

        transformationServicesHandler.discoverServices(new DiscoveryData(
                startupArgs.gameDirectory().toPath(), startupArgs.launchTarget(), startupArgs.programArgs()));

        // ITransformationService.class.getName(),
        // IModFileCandidateLocator.class.getName(),
        // IModFileReader.class.getName(),
        // IDependencyLocator.class.getName(),
        // TODO -> MANIFEST.MF declaration GraphicsBootstrapper.class.getName(),
        // TODO -> MANIFEST.MF declaration net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider.class.getName()

        // BUILD PLUGIN LAYER
        var scanResults = transformationServicesHandler.initializeTransformationServices(argumentHandler, environment)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        scanResults.getOrDefault(IModuleLayerManager.Layer.PLUGIN, List.of())
                .stream()
                .flatMap(resource -> resource.resources().stream())
                .forEach(np -> moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.PLUGIN, np));
        for (ModFile modFile : discoveryResult.pluginContent) {
            moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.GAME, modFile.getSecureJar());
        }
        moduleLayerHandler.buildLayer(IModuleLayerManager.Layer.PLUGIN);

        // BUILD GAME LAYER
        final var gameResults = transformationServicesHandler.triggerScanCompletion(moduleLayerHandler)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        final var gameContents = Stream.of(scanResults, gameResults)
                .flatMap(m -> m.getOrDefault(IModuleLayerManager.Layer.GAME, List.of()).stream())
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .toList();
        for (ModFile modFile : discoveryResult.gameContent) {
            moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.GAME, modFile.getSecureJar());
        }
        gameContents.forEach(j -> moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.GAME, j));

        transformationServicesHandler.initialiseServiceTransformers();
        for (var xform : getCoreModTransformers(launchContext)) {
            transformStore.addTransformer(xform, CoremodTransformationService.INSTANCE);
        }

        launchPluginHandler.offerScanResultsToPlugins(gameContents);
        // We do not do this: launchService.validateLaunchTarget(argumentHandler);
        var classLoader = transformationServicesHandler.buildTransformingClassLoader(launchPluginHandler, environment, moduleLayerHandler);

        // From here on out, try loading through the TCL
        Thread.currentThread().setContextClassLoader(classLoader);

        var gameLayer = moduleLayerHandler.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow();

        processAddOpensDeclarations(instrumentation, gameLayer);

        var gameRunner = commonLaunchHandler.launchService(startupArgs.programArgs(), gameLayer);

        try {
            gameRunner.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void processAddOpensDeclarations(Instrumentation instrumentation, ModuleLayer layer) {
        var additionalAddOpens = List.of(
                new AddOpensDeclaration("org.lwjgl", "org.lwjgl.system", "minecraft"));

        var groupedBySource = additionalAddOpens.stream().collect(Collectors.groupingBy(AddOpensDeclaration::module));
        for (var entry : groupedBySource.entrySet()) {
            var sourceModule = layer.findModule(entry.getKey()).orElse(null);
            if (sourceModule == null) {
                LOGGER.debug("Skipping add-opens declarations for {} since it does not exist.", entry.getKey());
                continue;
            }

            var groupedByPackage = entry.getValue().stream().collect(Collectors.groupingBy(AddOpensDeclaration::packageName));
            var extraOpens = new HashMap<String, Set<Module>>();
            for (var pkgEntry : groupedByPackage.entrySet()) {
                var packageName = pkgEntry.getKey();
                var targetModules = pkgEntry.getValue().stream()
                        .map(decl -> layer.findModule(decl.target()).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!targetModules.isEmpty()) {
                    extraOpens.put(packageName, targetModules);
                }
            }

            if (!extraOpens.isEmpty()) {
                LOGGER.info("Adding opens to {}: {}", sourceModule.getName(), extraOpens);
                instrumentation.redefineModule(
                        sourceModule,
                        Set.of(),
                        Map.of(),
                        extraOpens,
                        Set.of(),
                        Map.of());
            }
        }
    }

    record AddOpensDeclaration(String module, String packageName, String target) {}

    private static <T extends ILaunchPluginService> T addLaunchPlugin(Map<String, ILaunchPluginService> services,
            T service) {
        LOGGER.debug("Adding built-in launch plugin {}", service.name());
        services.put(service.name(), service);
        return service;
    }

    record FMLExternalOptions(
            @Nullable String neoForgeVersion,
            @Deprecated(forRemoval = true) @Nullable String fmlVersion,
            @Nullable String mcVersion,
            @Nullable String neoFormVersion) {}

    private static FMLExternalOptions parseArgs(String[] strings) {
        String neoForgeVersion = null;
        String mcVersion = null;
        String fmlVersion = null;
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
                    case "fmlVersion" -> fmlVersion = strings[++i];
                    case "mcVersion" -> mcVersion = strings[++i];
                    case "neoFormVersion" -> neoFormVersion = strings[++i];
                }
            }
        }

        return new FMLExternalOptions(neoForgeVersion, fmlVersion, mcVersion, neoFormVersion);
    }

    record DiscoveryResult(List<ModFile> pluginContent, List<ModFile> gameContent,
            List<ModLoadingIssue> discoveryIssues) {}

    private static DiscoveryResult runDiscovery(ILaunchContext launchContext) {
        var progress = StartupNotificationManager.prependProgressBar("Discovering mods...", 0);

        var additionalLocators = new ArrayList<IModFileCandidateLocator>();
        // TODO: We want to be rid of this, but functionality needs to be ported
        // TODO commonLaunchHandler.collectAdditionalModFileLocators(versionInfo, additionalLocators::add);

        additionalLocators.add(new GameLocator());
        additionalLocators.add(new ClasspathLibrariesLocator());

        var modDiscoverer = new ModDiscoverer(launchContext, additionalLocators);
        var modValidator = modDiscoverer.discoverMods();
        var pluginResources = modValidator.getPluginResources();

        // Now we should have a mod for "minecraft" and "neoforge" allowing us to fill-in the versions
        var neoForgeVersion = versionInfo.neoForgeVersion();
        var minecraftVersion = versionInfo.mcVersion();
        for (ModFile modResource : modValidator.getCandidateMods()) {
            var mods = modResource.getModFileInfo().getMods();
            if (mods.isEmpty()) {
                continue;
            }
            var mainMod = mods.getFirst();
            switch (mainMod.getModId()) {
                case "minecraft" -> minecraftVersion = mainMod.getVersion().toString();
                case "neoforge" -> neoForgeVersion = mainMod.getVersion().toString();
            }
        }
        versionInfo = new VersionInfo(
                neoForgeVersion,
                versionInfo().fmlVersion(),
                minecraftVersion,
                versionInfo().neoFormVersion());

        languageProviderLoader = new LanguageProviderLoader(launchContext);
        backgroundScanHandler = modValidator.stage2Validation();
        loadingModList = backgroundScanHandler.getLoadingModList();

        var gameResources = modValidator.getModResources();

        progress.complete();

        return new DiscoveryResult(
                pluginResources,
                gameResources,
                loadingModList.getModLoadingIssues());
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
            } catch (TimeoutException ignored) {}
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

    @Deprecated(forRemoval = true)
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

    static List<? extends ITransformer<?>> getCoreModTransformers(ILaunchContext launchContext) {
        LOGGER.debug(LOADING, "Loading coremod transformers");

        var result = new ArrayList<>(loadCoreModScripts());

        // Find all Java core mods
        for (var coreMod : ServiceLoaderUtil.loadServices(launchContext, ICoreMod.class)) {
            // Try to identify the mod-file this is from
            var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, coreMod);

            try {
                for (var transformer : coreMod.getTransformers()) {
                    LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), coreMod, sourceFile);
                    result.add(transformer);
                }
            } catch (Exception e) {
                // Throwing here would cause the game to immediately crash without a proper error screen,
                // since this method is called by ModLauncher directly.
                ModLoader.addLoadingIssue(
                        ModLoadingIssue.error("fml.modloadingissue.coremod_error", coreMod.getClass().getName(), sourceFile).withCause(e));
            }
        }

        return result;
    }

    private static List<ITransformer<?>> loadCoreModScripts() {
        var filesWithCoreModScripts = LoadingModList.get().getModFiles()
                .stream()
                .filter(mf -> !mf.getFile().getCoreMods().isEmpty())
                .toList();

        if (filesWithCoreModScripts.isEmpty()) {
            // Don't even bother starting the scripting engine if no mod contains scripting core mods
            LOGGER.debug(LogMarkers.CORE, "Not loading coremod script-engine since no mod requested it");
            return List.of();
        }

        LOGGER.info(LogMarkers.CORE, "Loading coremod script-engine for {}", filesWithCoreModScripts);
        try {
            return CoreModScriptLoader.loadCoreModScripts(filesWithCoreModScripts);
        } catch (NoClassDefFoundError e) {
            var message = "Could not find the coremod script-engine, but the following mods require it: " + filesWithCoreModScripts;
            ImmediateWindowHandler.crash(message);
            throw new IllegalStateException(message, e);
        }
    }
}
