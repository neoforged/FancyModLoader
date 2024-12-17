/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

import com.mojang.logging.LogUtils;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.LaunchServiceHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.ModuleLayerHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServicesHandler;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.module.Configuration;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
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
import net.neoforged.fml.loading.mixin.FMLCommandLineMixinContainer;
import net.neoforged.fml.loading.mixin.FMLMixinContainerHandle;
import net.neoforged.fml.loading.mixin.FMLModFileMixinContainer;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.locators.ClasspathLibrariesLocator;
import net.neoforged.fml.loading.moddiscovery.locators.GameLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevLocator;
import net.neoforged.fml.loading.moddiscovery.locators.MavenDirectoryLocator;
import net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeClientDataDevLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeClientDevLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeClientLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeServerDataDevLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeServerDevLaunchHandler;
import net.neoforged.fml.loading.targets.NeoForgeServerLaunchHandler;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.fmlstartup.FatalStartupException;
import net.neoforged.fmlstartup.api.StartupArgs;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.MixinLaunchPlugin;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;

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
    public static BackgroundScanHandler backgroundScanHandler;
    private static boolean production;
    @Nullable
    private static ModuleLayer gameLayer;
    @VisibleForTesting
    static DiscoveryResult discoveryResult;
    @VisibleForTesting
    static TransformingClassLoader gameClassLoader;

    @VisibleForTesting
    record DiscoveryResult(List<ModFile> pluginContent,
            List<ModFile> gameContent,
            List<ModLoadingIssue> discoveryIssues) {}

    // This is called by FML Startup
    @SuppressWarnings("unused")
    public static void startup(Instrumentation instrumentation, StartupArgs startupArgs) {
        // In dev, do not overwrite the logging configuration if the user explicitly set another one.
        // In production, always overwrite the vanilla configuration.
        // TODO: Update this comment and coordinate with launchers to determine how to use THEIR logging config
        if (System.getProperty("log4j2.configurationFile") == null) {
            overwriteLoggingConfiguration();
        }

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

        var moduleLayerHandler = new ModuleLayerHandler(startupArgs.parentClassLoader());
        var launchPlugins = new HashMap<String, ILaunchPluginService>();
        var launchHandlers = new HashMap<String, ILaunchHandlerService>();
        var environment = new Environment(
                s -> Optional.ofNullable(launchPlugins.get(s)),
                s -> Optional.ofNullable(launchHandlers.get(s)),
                moduleLayerHandler);
        environment.computePropertyIfAbsent(IEnvironment.Keys.MLSPEC_VERSION.get(), s -> "11");
        environment.computePropertyIfAbsent(IEnvironment.Keys.MLIMPL_VERSION.get(), s -> "11");
        environment.computePropertyIfAbsent(IEnvironment.Keys.MODLIST.get(), s -> new ArrayList<>());
        environment.computePropertyIfAbsent(IEnvironment.Keys.LAUNCHTARGET.get(), stringKey -> startupArgs.launchTarget());

        var programArgs = startupArgs.programArgs();
        var externalOptions = parseArgs(programArgs);

        gamePath = startupArgs.gameDirectory().toPath();
        FMLPaths.loadAbsolutePaths(gamePath);
        FMLConfig.load();

        ImmediateWindowHandler.load(startupArgs.launchTarget(), programArgs);

        LOGGER.debug(CORE, "Searching for launch handler '{}'", startupArgs.launchTarget());
        commonLaunchHandler = getLaunchHandler(startupArgs.launchTarget());

        // TODO: These should be determine via ambient information too
        dist = commonLaunchHandler.getDist();
        production = commonLaunchHandler.isProduction();

        var launchContext = new LaunchContext(
                environment,
                dist,
                startupArgs.gameDirectory().toPath(),
                moduleLayerHandler,
                List.of(), // TODO: Argparse
                List.of(), // TODO: Argparse
                List.of(), // TODO: Argparse
                startupArgs.unclaimedClassPathEntries());
        for (var claimedFile : startupArgs.claimedFiles()) {
            launchContext.addLocated(claimedFile.toPath());
        }

        launchHandlerName = commonLaunchHandler.name();
        versionInfo = new VersionInfo(
                externalOptions.neoForgeVersion(),
                externalOptions.fmlVersion(),
                externalOptions.mcVersion(),
                externalOptions.neoFormVersion());

        // Only register the one we already know is selected
        launchHandlers.put(commonLaunchHandler.name(), commonLaunchHandler);
        FMLEnvironment.setupInteropEnvironment(environment);
        net.neoforged.neoforgespi.Environment.build(environment);

        // Add our own launch plugins explicitly. These do need to exist before mod discovery,
        // as mod discovery will add its results to these engines directly.
        accessTransformer = addLaunchPlugin(launchContext, launchPlugins, new AccessTransformerService()).engine;
        addLaunchPlugin(launchContext, launchPlugins, new RuntimeEnumExtender());
        runtimeDistCleaner = addLaunchPlugin(launchContext, launchPlugins, new RuntimeDistCleaner(dist));

        discoveryResult = runOffThread(() -> runDiscovery(launchContext));

        for (var issue : discoveryResult.discoveryIssues()) {
            LOGGER.atLevel(issue.severity() == ModLoadingIssue.Severity.ERROR ? Level.ERROR : Level.WARN)
                    .setCause(issue.cause())
                    .log("{}", FMLTranslations.translateIssueEnglish(issue));
        }

        // ML would usually handle these two arguments
        var launchService = new LaunchServiceHandler(Stream.empty());

        // Discover third party launch plugins
        for (var launchPlugin : ServiceLoaderUtil.loadServices(
                launchContext,
                ILaunchPluginService.class,
                List.of(new MixinLaunchPlugin()),
                FMLLoader::isValidLaunchPlugin)) {
            addLaunchPlugin(launchContext, launchPlugins, launchPlugin);
        }

        // Discover third party transformation services
        var transformationServices = ServiceLoaderUtil.loadServices(launchContext, ITransformationService.class);

        var transformStore = new TransformStore();
        var transformationServicesHandler = new TransformationServicesHandler(transformStore, moduleLayerHandler, environment, transformationServices);
        var argumentHandler = new ArgumentHandler(programArgs);
        var launchPluginHandler = new LaunchPluginHandler(launchPlugins.values().stream());

        Launcher.INSTANCE = new Launcher(
                transformationServicesHandler,
                environment,
                transformStore,
                launchService,
                launchPluginHandler,
                moduleLayerHandler);

        // IModFileCandidateLocator.class.getName(),
        // IModFileReader.class.getName(),
        // IDependencyLocator.class.getName(),
        // TODO -> MANIFEST.MF declaration GraphicsBootstrapper.class.getName(),
        // TODO -> MANIFEST.MF declaration net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider.class.getName()

        // BUILD SERVICES LAYER
        buildUntransformedLayer(instrumentation, moduleLayerHandler, IModuleLayerManager.Layer.SERVICE, startupArgs.parentClassLoader());

        // BUILD PLUGIN LAYER
        var scanResults = transformationServicesHandler.initializeTransformationServices(argumentHandler, environment)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));

        // Register the container (will use the platform agent)
        MixinBootstrap.getPlatform().addContainer(new FMLMixinContainerHandle());

        scanResults.getOrDefault(IModuleLayerManager.Layer.PLUGIN, List.of())
                .stream()
                .flatMap(resource -> resource.resources().stream())
                .forEach(np -> moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.PLUGIN, np));
        for (var modFileInfo : loadingModList.getPlugins()) {
            moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.PLUGIN, modFileInfo.getFile().getSecureJar());
        }
        buildUntransformedLayer(instrumentation, moduleLayerHandler, IModuleLayerManager.Layer.PLUGIN, startupArgs.parentClassLoader());

        // Now go and build the language providers and let mods discover theirs
        languageProviderLoader = new LanguageProviderLoader(launchContext);
        for (var modFile : discoveryResult.gameContent) {
            modFile.identifyLanguage();
        }

        // BUILD GAME LAYER
        var gameResults = transformationServicesHandler.triggerScanCompletion(moduleLayerHandler)
                .stream().collect(Collectors.groupingBy(ITransformationService.Resource::target));
        var gameContents = Stream.of(scanResults, gameResults)
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

        // This step is required so that mixin loads mixinextras, for example
        var mergedGameContent = new ArrayList<>(gameContents);
        for (var modFile : discoveryResult.gameContent) {
            mergedGameContent.add(modFile.getSecureJar());
        }
        launchPluginHandler.offerScanResultsToPlugins(mergedGameContent);
        // We do not do this: launchService.validateLaunchTarget(argumentHandler);
        // We inlined this: transformationServicesHandler.buildTransformingClassLoader...
        var gameLayerInfo = moduleLayerHandler.buildLayer(IModuleLayerManager.Layer.GAME,
                (cf, parents) -> {
                    var moduleNames = getModuleNameList(cf);
                    LOGGER.info("Building module layer GAME:\n{}", moduleNames);
                    return new TransformingClassLoader(transformStore, launchPluginHandler, environment, cf, parents);
                });
        gameClassLoader = (TransformingClassLoader) gameLayerInfo.cl();
        if (startupArgs.parentClassLoader() != null) {
            gameClassLoader.setFallbackClassLoader(startupArgs.parentClassLoader());
        }
        // TODO: No idea why this is here
        moduleLayerHandler.updateLayer(IModuleLayerManager.Layer.PLUGIN, li -> li.cl().setFallbackClassLoader(gameClassLoader));

        var gameLayer = gameLayerInfo.layer();

        ModuleAccessDeclarations.apply(instrumentation, gameLayer);

        // From here on out, try loading through the TCL
        Thread.currentThread().setContextClassLoader(gameClassLoader);

        // We're adding mixins *after* setting the Thread context classloader since
        // Mixin stubbornly loads Mixin Configs via its ModLauncher environment using the TCL.
        // Adding containers beforehand will try to load Mixin configs using the app classloader and fail.
        // TODO: When we have our own mixinservice, make it use the loading modlist to load resources directly, bypassing the TCL. Then we can move it up again.
        addMixins(loadingModList);

        // This will initialize Mixins, for example
        launchPluginHandler.announceLaunch(gameClassLoader, new NamedPath[0]);

        var gameRunner = commonLaunchHandler.launchService(programArgs, gameLayer);
        if (startupArgs.skipEntryPoint()) {
            return;
        }

        try {
            gameRunner.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void addMixins(LoadingModList loadingModList) {
        // Add extra mixin configs from command line
        var extraMixinConfigs = System.getProperty("fml.extraMixinConfigs");
        if (extraMixinConfigs != null) {
            var extraMixinConfigList = Arrays.asList(extraMixinConfigs.split(File.pathSeparator));
            MixinBootstrap.getPlatform().addContainer(new FMLCommandLineMixinContainer(extraMixinConfigList));
        }

        for (var modFile : loadingModList.getModFiles()) {
            var modMixins = modFile.getFile().getMixinConfigs();
            if (!modMixins.isEmpty()) {
                MixinBootstrap.getPlatform().addContainer(new FMLModFileMixinContainer(modFile.getFile()));
            }
        }
    }

    private static void buildUntransformedLayer(
            Instrumentation instrumentation,
            ModuleLayerHandler moduleLayerHandler,
            IModuleLayerManager.Layer layer,
            ClassLoader parentClassloader) {
        var li = moduleLayerHandler.buildLayer(
                layer,
                (cf, p) -> {
                    var moduleNames = getModuleNameList(cf);
                    LOGGER.info("Building module layer {}:\n{}", layer.name(), moduleNames);
                    return new ModuleClassLoader("LAYER " + layer.name(), cf, p, parentClassloader);
                });
        ModuleAccessDeclarations.apply(instrumentation, li.layer());
    }

    private static String getModuleNameList(Configuration cf) {
        return cf.modules().stream()
                .map(module -> " - " + module.name() + " (" + getNiceModuleLocation(module.reference()) + ")")
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private static String getNiceModuleLocation(ModuleReference moduleRef) {
        return moduleRef.location().map(URI::getPath).orElse("-");
    }

    private static <T extends ILaunchPluginService> boolean isValidLaunchPlugin(Class<T> serviceClass) {
        // Blacklist any plugins we add ourselves
        if (serviceClass == AccessTransformerService.class) {
            return false;
        }

        // Blacklist the pre Mixin Modlauncher 9 plugin which gets picked up if mixin wasn't loaded as a module
        // Since we're adding the Mixin service manually, we'll blacklist all auto-detected ones here.
        if (serviceClass == MixinLaunchPluginLegacy.class || serviceClass == MixinLaunchPlugin.class) {
            return false;
        }
        return true;
    }

    private static CommonLaunchHandler getLaunchHandler(String name) {
        return switch (name) {
            case "neoforgeclient" -> new NeoForgeClientLaunchHandler();
            case "neoforgeclientdev" -> new NeoForgeClientDevLaunchHandler();
            case "neoforgeclientdatadev" -> new NeoForgeClientDataDevLaunchHandler();
            case "neoforgeserverdatadev" -> new NeoForgeServerDataDevLaunchHandler();
            case "neoforgeserver" -> new NeoForgeServerLaunchHandler();
            case "neoforgeserverdev" -> new NeoForgeServerDevLaunchHandler();
            // Default to client
            case null -> new NeoForgeClientLaunchHandler();
            default -> {
                var handler = ServiceLoader.load(ILaunchHandlerService.class)
                        .stream()
                        .map(provider -> {
                            try {
                                return provider.get();
                            } catch (Exception e) {
                                LOGGER.error("Failed to instantiate launch handler service {}", provider.type(), e);
                                return null;
                            }
                        })
                        .filter(s -> s != null && Objects.equals(name, s.name()))
                        .findFirst()
                        .orElse(null);

                if (handler == null) {
                    LOGGER.info("Failed to find a valid launch handler. Defaulting to client.");
                    throw new FatalStartupException("The given launch target is unknown: " + name);
                } else if (handler instanceof CommonLaunchHandler clh) {
                    yield clh;
                } else {
                    throw new FatalStartupException("The given launch target is incompatible with FML: " + handler);
                }
            }
        };
    }

    private static <T extends ILaunchPluginService> T addLaunchPlugin(ILaunchContext launchContext,
            Map<String, ILaunchPluginService> services,
            T service) {
        LOGGER.debug("Adding launch plugin {}", service.name());
        var previous = services.put(service.name(), service);
        if (previous != null) {
            var source1 = ServiceLoaderUtil.identifySourcePath(launchContext, service);
            var source2 = ServiceLoaderUtil.identifySourcePath(launchContext, previous);

            throw new FatalStartupException("Multiple launch plugin services of the same name '"
                    + previous.name() + "' are present: " + source1 + " and " + source2);
        }
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
                    case "fml.neoForgeVersion" -> neoForgeVersion = strings[++i];
                    case "fml.fmlVersion" -> fmlVersion = strings[++i];
                    case "fml.mcVersion" -> mcVersion = strings[++i];
                    case "fml.neoFormVersion" -> neoFormVersion = strings[++i];
                }
            }
        }

        return new FMLExternalOptions(neoForgeVersion, fmlVersion, mcVersion, neoFormVersion);
    }

    private static DiscoveryResult runDiscovery(ILaunchContext launchContext) {
        var progress = StartupNotificationManager.prependProgressBar("Discovering mods...", 0);

        var additionalLocators = new ArrayList<IModFileCandidateLocator>();
        // TODO: We want to be rid of this, but functionality needs to be ported
        // TODO commonLaunchHandler.collectAdditionalModFileLocators(versionInfo, additionalLocators::add);

        additionalLocators.add(new GameLocator());
        additionalLocators.add(new InDevLocator());
        additionalLocators.add(new ModsFolderLocator());
        additionalLocators.add(new MavenDirectoryLocator());
        additionalLocators.add(new ClasspathLibrariesLocator());

        var modDiscoverer = new ModDiscoverer(launchContext, additionalLocators);
        var discoveryResult = modDiscoverer.discoverMods();
        var modFiles = new ArrayList<>(discoveryResult.modFiles());
        var issues = new ArrayList<>(discoveryResult.discoveryIssues());

        // this.candidateMods = lst(modFiles.get(IModFile.Type.MOD));
        //        this.candidateMods.addAll(lst(modFiles.get(IModFile.Type.GAMELIBRARY)));
        // Validate the loading.
        // With a deduplicated list, we can now successfully process the artifacts and load
        // transformer plugins.
        // for (Iterator<ModFile> iterator = mods.iterator(); iterator.hasNext();) {
        //     var modFile = iterator.next();
        //     if (!modFile.identifyMods()) {
        //         LOGGER.warn(LogMarkers.SCAN, "File {} has been ignored - it is invalid", modFile.getFilePath());
        //         iterator.remove();
        //     }
        // }
        // TODO validateFiles(candidateMods);
        // TODO if (LOGGER.isDebugEnabled(LogMarkers.SCAN)) {
        // TODO     LOGGER.debug(LogMarkers.SCAN, "Found {} mod files with {} mods", candidateMods.size(), candidateMods.stream().mapToInt(mf -> mf.getModInfos().size()).sum());
        // TODO }
        // TODO ImmediateWindowHandler.updateProgress("Found " + candidateMods.size() + " mod candidates");

        // Now we should have a mod for "minecraft" and "neoforge" allowing us to fill in the versions
        List<ModFile> fallbackModFiles = new ArrayList<>();
        var neoForgeVersion = versionInfo.neoForgeVersion();
        var minecraftVersion = versionInfo.mcVersion();
        for (var modFile : discoveryResult.modFiles()) {
            var mods = modFile.getModFileInfo().getMods();
            if (mods.isEmpty()) {
                continue;
            }
            var mainMod = mods.getFirst();
            switch (mainMod.getModId()) {
                case "minecraft" -> {
                    minecraftVersion = mainMod.getVersion().toString();
                    fallbackModFiles.add(modFile);
                }
                case "neoforge" -> {
                    neoForgeVersion = mainMod.getVersion().toString();
                    fallbackModFiles.add(modFile);
                }
            }
        }
        versionInfo = new VersionInfo(
                neoForgeVersion,
                versionInfo().fmlVersion(),
                minecraftVersion,
                versionInfo().neoFormVersion());

        progress.complete();

        var gameContent = new ArrayList<ModFile>(discoveryResult.modFiles().size());
        var pluginContent = new ArrayList<ModFile>(discoveryResult.modFiles().size());
        for (var modFile : discoveryResult.modFiles()) {
            if (modFile.getType() == IModFile.Type.LIBRARY) {
                pluginContent.add(modFile);
            } else {
                gameContent.add(modFile);
            }
        }

        loadingModList = ModSorter.sort(pluginContent, gameContent, issues);

        backgroundScanHandler = new BackgroundScanHandler();
        backgroundScanHandler.setLoadingModList(loadingModList);

        Map<IModInfo, Path> enumExtensionsByMod = new HashMap<>();
        for (var modFile : modFiles) {
            if (!modFile.identifyMods()) {
                continue;
            }

            for (var accessTransformer : modFile.getAccessTransformers()) {
                FMLLoader.addAccessTransformer(accessTransformer, modFile);
            }

            var mods = modFile.getModInfos();
            var primaryModId = modFile.getPrimaryModId();
            for (var mixinConfig : modFile.getMixinConfigs()) {
                DeferredMixinConfigRegistration.addMixinConfig(mixinConfig, primaryModId);
            }

            for (var mod : mods) {
                mod.getConfig().<String>getConfigElement("enumExtensions").ifPresent(file -> {
                    Path path = mod.getOwningFile().getFile().findResource(file);
                    if (!Files.isRegularFile(path)) {
                        ModLoader.addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.enumextender.file_not_found", path).withAffectedMod(mod));
                        return;
                    }
                    enumExtensionsByMod.put(mod, path);
                });
            }

            backgroundScanHandler.submitForScanning(modFile);
        }
        RuntimeEnumExtender.loadEnumPrototypes(enumExtensionsByMod);

        return new DiscoveryResult(
                pluginContent,
                gameContent,
                issues);
    }

    private static <T> T runOffThread(Supplier<T> supplier) {
        var cl = Thread.currentThread().getContextClassLoader();
        var future = CompletableFuture.supplyAsync(() -> {
            var previousCl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(cl);
            try {
                return supplier.get();
            } catch (Throwable e) {
                LOGGER.error("Off-thread operation failed.", e);
                throw new CompletionException(e);
            } finally {
                Thread.currentThread().setContextClassLoader(previousCl);
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
        LOGGER.debug(CORE, "FML {} loading", version);
        LOGGER.debug(CORE, "FML found ModLauncher version : {}", environment.getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElse("unknown"));

        accessTransformer = ((AccessTransformerService) environment.findLaunchPlugin("accesstransformer").orElseThrow(() -> {
            LOGGER.error(CORE, "Access Transformer library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing AccessTransformer, cannot run");
        })).engine;

        runtimeDistCleaner = (RuntimeDistCleaner) environment.findLaunchPlugin("runtimedistcleaner").orElseThrow(() -> {
            LOGGER.error(CORE, "Dist Cleaner is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing DistCleaner, cannot run!");
        });
        LOGGER.debug(CORE, "Found Runtime Dist Cleaner");

        try {
            LOGGER.debug(CORE, "FML found EventBus version : {}", JarVersionLookupHandler.getVersion(IEventBus.class).orElse("UNKNOWN"));
        } catch (NoClassDefFoundError e) {
            LOGGER.error(CORE, "Event Bus library is missing, we need this to run");
            throw new IncompatibleEnvironmentException("Missing EventBus, cannot run");
        }

        try {
            Class.forName("com.electronwill.nightconfig.core.Config", false, environment.getClass().getClassLoader());
            Class.forName("com.electronwill.nightconfig.toml.TomlFormat", false, environment.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error(CORE, "Failed to load NightConfig");
            throw new IncompatibleEnvironmentException("Missing NightConfig");
        }
    }

    @Deprecated(forRemoval = true)
    static void setupLaunchHandler(VersionInfo versionInfo, CommonLaunchHandler launchHandler) {
        commonLaunchHandler = launchHandler;
        launchHandlerName = launchHandler.name();
        gamePath = FMLPaths.GAMEDIR.get();
        FMLLoader.versionInfo = versionInfo;

        dist = launchHandler.getDist();
        production = launchHandler.isProduction();

        if (runtimeDistCleaner != null) {
            runtimeDistCleaner.setDistribution(dist);
        }
    }

    @Deprecated(forRemoval = true)
    public static List<ITransformationService.Resource> beginModScan(ILaunchContext launchContext) {
        // TODO var additionalLocators = new ArrayList<IModFileCandidateLocator>();
        // TODO commonLaunchHandler.collectAdditionalModFileLocators(versionInfo, additionalLocators::add);
// TODO
        // TODO var modDiscoverer = new ModDiscoverer(launchContext, additionalLocators);
        // TODO modValidator = modDiscoverer.discoverMods();
        // TODO var pluginResources = modValidator.getPluginResources();
        // TODO return List.of(new ITransformationService.Resource(IModuleLayerManager.Layer.PLUGIN, pluginResources.stream().map(IModFile::getSecureJar).toList()));
        throw new UnsupportedOperationException();
    }

    @Deprecated(forRemoval = true)
    public static List<ITransformationService.Resource> completeScan(ILaunchContext launchContext, List<String> extraMixinConfigs) {
        // TODO languageProviderLoader = new LanguageProviderLoader(launchContext);
        // TODO backgroundScanHandler = modValidator.stage2Validation();
        // TODO loadingModList = backgroundScanHandler.getLoadingModList();
        // TODO if (!loadingModList.hasErrors()) {
        // TODO     // Add extra mixin configs
        // TODO     extraMixinConfigs.forEach(DeferredMixinConfigRegistration::addMixinConfig);
        // TODO }
        // TODO return List.of(
        // TODO         new ITransformationService.Resource(IModuleLayerManager.Layer.GAME, modValidator.getModResources().stream().map(ModFile::getSecureJar).toList()));

        throw new UnsupportedOperationException();
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
        var filesWithCoreModScripts = loadingModList.getModFiles()
                .stream()
                .filter(mf -> !mf.getFile().getCoreMods().isEmpty())
                .toList();

        if (filesWithCoreModScripts.isEmpty()) {
            // Don't even bother starting the scripting engine if no mod contains scripting core mods
            LOGGER.debug(CORE, "Not loading coremod script-engine since no mod requested it");
            return List.of();
        }

        LOGGER.info(CORE, "Loading coremod script-engine for {}", filesWithCoreModScripts);
        try {
            return CoreModScriptLoader.loadCoreModScripts(filesWithCoreModScripts);
        } catch (NoClassDefFoundError e) {
            var message = "Could not find the coremod script-engine, but the following mods require it: " + filesWithCoreModScripts;
            ImmediateWindowHandler.crash(message);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Forces the log4j2 logging context to use the configuration shipped with fml_loader.
     */
    private static void overwriteLoggingConfiguration() {
        var loggingConfigUrl = FMLLoader.class.getResource("/log4j2.xml");
        if (loggingConfigUrl != null) {
            URI loggingConfigUri;
            try {
                loggingConfigUri = loggingConfigUrl.toURI();
            } catch (URISyntaxException e) {
                LOGGER.error("Failed to read FML logging configuration: {}", loggingConfigUrl, e);
                return;
            }
            LOGGER.info("Reconfiguring logging with configuration from {}", loggingConfigUri);
            var configSource = ConfigurationSource.fromUri(loggingConfigUri);
            Configurator.reconfigure(ConfigurationFactory.getInstance().getConfiguration(LoggerContext.getContext(), configSource));
        }
    }
}
