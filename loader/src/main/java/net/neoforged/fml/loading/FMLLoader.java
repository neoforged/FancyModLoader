/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import cpw.mods.niofs.union.UnionFileSystem;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.locators.ClasspathLibrariesLocator;
import net.neoforged.fml.loading.moddiscovery.locators.GameLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevLocator;
import net.neoforged.fml.loading.moddiscovery.locators.MavenDirectoryLocator;
import net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.startup.FMLStartupContext;
import net.neoforged.fml.startup.FatalStartupException;
import net.neoforged.fml.startup.StartupArgs;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FMLLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static AccessTransformerEngine accessTransformer;
    private static LanguageProviderLoader languageProviderLoader;
    private static Dist dist;
    private static LoadingModList loadingModList;
    private static Path gamePath;
    private static VersionInfo versionInfo;
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
                           List<ModLoadingIssue> discoveryIssues) {
    }

    // This is called by FML Startup
    public static FMLStartupContext startup(@Nullable Instrumentation instrumentation, StartupArgs startupArgs) {
        detectDistAndProduction(startupArgs);

        LOGGER.info(
                "Starting FancyModLoader version {} ({} in {})",
                JarVersionLookupHandler.getVersion(FMLLoader.class).orElse("UNKNOWN"),
                dist,
                production ? "PROD" : "DEV");

        // Make UnionFS work
        // TODO: This should come from a manifest? Service-Loader? Something...
        if (instrumentation != null) {
            instrumentation.redefineModule(
                    MethodHandle.class.getModule(),
                    Set.of(),
                    Map.of(),
                    Map.of("java.lang.invoke", Set.of(SecureJar.class.getModule())),
                    Set.of(),
                    Map.of());
        }

        var launchPlugins = new HashMap<String, ILaunchPluginService>();

        var programArgs = ProgramArgs.from(startupArgs.programArgs());

        gamePath = startupArgs.gameDirectory().toPath();
        FMLPaths.loadAbsolutePaths(gamePath);
        FMLConfig.load();

        var launchContext = new LaunchContext(
                dist,
                startupArgs.gameDirectory().toPath(),
                List.of(), // TODO: Argparse
                List.of(), // TODO: Argparse
                List.of(), // TODO: Argparse
                startupArgs.unclaimedClassPathEntries());
        for (var claimedFile : startupArgs.claimedFiles()) {
            launchContext.addLocated(claimedFile.toPath());
        }

        // Search for early services
        var resourcesToClose = new ArrayList<AutoCloseable>();
        var parentLoader = Objects.requireNonNullElse(startupArgs.parentClassLoader(), ClassLoader.getSystemClassLoader());
        var earlyServices = EarlyServiceDiscovery.findEarlyServices(FMLPaths.MODSDIR.get());
        // TODO: Early services should participate in JIJ discovery
        parentLoader = loadEarlyServices(launchContext, parentLoader, earlyServices);

        ImmediateWindowHandler.load(startupArgs.headless(), programArgs);

        versionInfo = new VersionInfo(
                programArgs.remove("fml.neoForgeVersion"),
                programArgs.remove("fml.fmlVersion"),
                programArgs.remove("fml.mcVersion"),
                programArgs.remove("fml.neoFormVersion")
        );

        var mixinFacade = new MixinFacade();

        // Add our own launch plugins explicitly. These do need to exist before mod discovery,
        // as mod discovery will add its results to these engines directly.
        accessTransformer = addLaunchPlugin(launchContext, launchPlugins, new AccessTransformerService()).engine;
        addLaunchPlugin(launchContext, launchPlugins, new RuntimeEnumExtender());
        addLaunchPlugin(launchContext, launchPlugins, new RuntimeDistCleaner(dist));
        addLaunchPlugin(launchContext, launchPlugins, mixinFacade.getLaunchPlugin());

        discoveryResult = runOffThread(() -> runDiscovery(launchContext));
        ClassLoadingGuardian classLoadingGuardian = null;
        if (instrumentation != null) {
            classLoadingGuardian = new ClassLoadingGuardian(instrumentation, discoveryResult.gameContent);
        }

        for (var issue : discoveryResult.discoveryIssues()) {
            LOGGER.atLevel(issue.severity() == ModLoadingIssue.Severity.ERROR ? Level.ERROR : Level.WARN)
                    .setCause(issue.cause())
                    .log("{}", FMLTranslations.translateIssueEnglish(issue));
        }

        // Discover third party launch plugins
        for (var launchPlugin : ServiceLoaderUtil.loadServices(
                launchContext,
                ILaunchPluginService.class,
                List.of(),
                FMLLoader::isValidLaunchPlugin)) {
            addLaunchPlugin(launchContext, launchPlugins, launchPlugin);
        }

        var launchPluginHandler = new LaunchPluginHandler(launchPlugins.values().stream());

        // This is to satisfy Synitra
        Launcher.INSTANCE = new Launcher(launchPluginHandler);

        // TODO -> MANIFEST.MF declaration GraphicsBootstrapper.class.getName(),
        // TODO -> MANIFEST.MF declaration net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider.class.getName()

        // Load Plugins
        if (!loadingModList.getPlugins().isEmpty()) {
            var pluginLoader = loadPlugins(launchContext, startupArgs.cacheRoot(), parentLoader, loadingModList.getPlugins());
            resourcesToClose.add(pluginLoader);
            Thread.currentThread().setContextClassLoader(pluginLoader);
            parentLoader = pluginLoader;
        }

        // Now go and build the language providers and let mods discover theirs
        languageProviderLoader = new LanguageProviderLoader(launchContext);
        for (var modFile : discoveryResult.gameContent) {
            modFile.identifyLanguage();
        }

        // BUILD GAME LAYER
        // NOTE: This is where Mixin contributes its synthetic SecureJar to ensure it's generated classes are handled by the TCL
        var gameContent = new ArrayList<SecureJar>();
        for (var modFile : discoveryResult.gameContent) {
            gameContent.add(modFile.getSecureJar());
        }
        // Add generated code container for Mixin
        // TODO: This needs a new API for plugins to contribute synthetic modules, *OR* it should go into the mod-file discovery!
        gameContent.add(mixinFacade.createGeneratedCodeContainer());
        launchPluginHandler.offerScanResultsToPlugins(gameContent);

        // We do not do this: launchService.validateLaunchTarget(argumentHandler);
        // We inlined this: transformationServicesHandler.buildTransformingClassLoader...

        var classTransformer = ClassTransformerFactory.create(launchContext, launchPluginHandler, loadingModList);
        var gameLayerResult = buildGameModuleLayer(classTransformer, gameContent, List.of(ModuleLayer.boot()), parentLoader);
        gameLayerResult.classLoader.setFallbackClassLoader(parentLoader);

        gameLayer = gameLayerResult.gameLayer();
        gameClassLoader = gameLayerResult.classLoader;

        // From here on out, try loading through the TCL
        if (classLoadingGuardian != null) {
            classLoadingGuardian.end(gameClassLoader);
        }
        resourcesToClose.add(makeClassLoaderCurrent());

        // We're adding mixins *after* setting the Thread context classloader since
        // Mixin stubbornly loads Mixin Configs via its ModLauncher environment using the TCL.
        // Adding containers beforehand will try to load Mixin configs using the app classloader and fail.
        mixinFacade.finishInitialization(loadingModList, gameClassLoader);

        // This will initialize Mixins, for example
        launchPluginHandler.announceLaunch(gameClassLoader, new NamedPath[0]);

        ImmediateWindowHandler.acceptGameLayer(gameLayer);
        ImmediateWindowHandler.updateProgress("Launching minecraft");
        progressWindowTick.run();

        return new FMLStartupContext(LOGGER, programArgs, gameClassLoader, resourcesToClose);
    }

    private static void detectDistAndProduction(StartupArgs args) {
        if (args.forcedDist() != null) {
            dist = args.forcedDist();
            production = false;
            return;
        }

        // If a client class is available, then it's client, otherwise DEDICATED_SERVER
        // The auto-detection never detects JOINED since it's impossible to do so
        var cl = Objects.requireNonNullElse(args.parentClassLoader(), Thread.currentThread().getContextClassLoader());
        var clientAvailable = cl.getResource("net/minecraft/client/main/Main.class") != null;
        var serverAvailable = cl.getResource("net/minecraft/server/Main.class") != null;
        var unobfuscatedClassAvailable = cl.getResource("net/minecraft/DetectedVersion.class") != null;
        LOGGER.debug("Dist detection: clientAvailable={} serverAvailable={} unobfuscatedClassAvailable={}",
                clientAvailable, serverAvailable, unobfuscatedClassAvailable);

        dist = clientAvailable ? Dist.CLIENT : Dist.DEDICATED_SERVER;

        // We are in production when the Server entrypoint exists, but not a usually obfuscated canary class
        production = serverAvailable && !unobfuscatedClassAvailable;
    }

    /**
     * Loads the given plugin into a URL classloader.
     */
    private static URLClassLoader loadEarlyServices(
            ILaunchContext launchContext,
            ClassLoader parentLoader,
            List<Path> services) {
        List<URL> rootUrls = new ArrayList<>(services.size());
        for (var service : services) {
            try {
                rootUrls.add(service.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e); // This should not happen for file URLs
            }
            launchContext.addLocated(service); // Prevents it from getting picked up again
        }

        return new URLClassLoader("FML Early Services", rootUrls.toArray(URL[]::new), parentLoader);
    }

    /**
     * Loads the given plugin into a URL classloader.
     */
    private static URLClassLoader loadPlugins(
            ILaunchContext launchContext,
            Path cacheDir,
            ClassLoader parentLoader,
            List<IModFileInfo> plugins) {
        // Causes URL handler to be initialized
        new ModuleClassLoader("dummy", Configuration.empty(), List.of(ModuleLayer.empty()));

        List<URL> rootUrls = new ArrayList<>(plugins.size());
        for (var plugin : plugins) {
            var jar = plugin.getFile().getSecureJar();

            var basePaths = getBasePaths(jar);

            for (var basePath : basePaths) {
                try {
                    if (basePath.getFileSystem() == FileSystems.getDefault()) {
                        rootUrls.add(basePath.toUri().toURL());
                    } else {
                        try {
                            var jarInMemory = Files.readAllBytes(basePath);
                            MessageDigest md = MessageDigest.getInstance("MD5");
                            var hash = HexFormat.of().formatHex(md.digest(jarInMemory));

                            var jarCacheDir = cacheDir
                                    .resolve("embedded_jars")
                                    .resolve(hash);
                            Files.createDirectories(jarCacheDir);
                            var cachedFile = jarCacheDir.resolve(jar.name() + ".jar");
                            long expectedSize = jarInMemory.length;
                            long existingSize = -1;
                            try {
                                existingSize = Files.size(cachedFile);
                            } catch (IOException ignored) {
                            }
                            if (existingSize != expectedSize) {
                                // TODO atomic move crap
                                Files.write(cachedFile, jarInMemory);
                            }

                            launchContext.setJarSourceDescription(
                                    cachedFile,
                                    formatModFileLocation(launchContext, plugin.getFile()));

                            rootUrls.add(cachedFile.toUri().toURL());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e); // TODO error handling: investigate when this can happen
                }
            }
        }

        return new URLClassLoader(
                "FML Plugins",
                rootUrls.toArray(URL[]::new),
                parentLoader);
    }

    private static String formatModFileLocation(ILaunchContext launchContext, IModFile file) {
        var info = launchContext.relativizePath(file.getFilePath());

        var parentFile = file.getDiscoveryAttributes().parent();
        if (parentFile != null) {
            info = formatModFileLocation(launchContext, parentFile) + " > " + info;
        }

        return info;
    }

    private static List<Path> getBasePaths(SecureJar jar) {
        var unionFs = (UnionFileSystem) jar.getRootPath().getFileSystem();
        if (unionFs.getFilesystemFilter() != null) {
            throw new IllegalStateException("Filtering for plugin jars is not supported: " + jar);
        }

        try {
            var getBasePaths = UnionFileSystem.class.getDeclaredMethod("getBasePaths");
            getBasePaths.setAccessible(true);
            //noinspection unchecked
            return (List<Path>) getBasePaths.invoke(unionFs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static GameLayerResult buildGameModuleLayer(ClassTransformer classTransformer,
                                                        List<SecureJar> content,
                                                        List<ModuleLayer> parentLayers,
                                                        ClassLoader parentLoader) {
        long start = System.currentTimeMillis();

        var cf = Configuration.resolveAndBind(
                JarModuleFinder.of(content.toArray(SecureJar[]::new)),
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                content.stream().map(SecureJar::name).toList());

        var moduleNames = getModuleNameList(cf);
        LOGGER.info("Building module layer GAME:\n{}", moduleNames);
        var loader = new TransformingClassLoader(classTransformer, cf, parentLayers, parentLoader);

        var layer = ModuleLayer.defineModules(
                cf,
                parentLayers,
                f -> loader).layer();

        var elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Built game layer in {}ms", elapsed);

        return new GameLayerResult(layer, loader);
    }

    record GameLayerResult(ModuleLayer gameLayer, TransformingClassLoader classLoader) {
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

        // Blacklist all Mixin services, since we implement all of them ourselves
        return !isMixinServiceClass(serviceClass);
    }

    public static boolean isMixinServiceClass(Class<?> serviceClass) {
        // Blacklist all Mixin services, since we implement all of them ourselves
        var packageName = serviceClass.getPackageName();
        return packageName.equals("org.spongepowered.asm.launch") || packageName.startsWith("org.spongepowered.asm.launch.");
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
                versionInfo().neoFormVersion()
        );

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
            } catch (TimeoutException ignored) {
            }
        }
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
    }

    public static LoadingModList getLoadingModList() {
        return loadingModList;
    }

    public static Path getGamePath() {
        return gamePath;
    }

    @Deprecated(forRemoval = true)
    public static String getLauncherInfo() {
        return "";
    }

    @Deprecated(forRemoval = true)
    public static List<Map<String, String>> modLauncherModList() {
        return Collections.emptyList();
    }

    @Deprecated(forRemoval = true)
    public static String launcherHandlerName() {
        return "";
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

    /**
     * @return An AutoClosable that resets the classloader back to the original.
     */
    private static AutoCloseable makeClassLoaderCurrent() {
        var previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(gameClassLoader);
        return () -> {
            if (Thread.currentThread().getContextClassLoader() == gameClassLoader) {
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        };
    }
}
