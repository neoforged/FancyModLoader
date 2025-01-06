/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.VirtualJar;
import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.ResourceMaskingClassLoader;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import cpw.mods.niofs.union.UnionFileSystem;
import java.io.File;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.FMLVersion;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.jfr.ClassTransformerProfiler;
import net.neoforged.fml.loading.mixin.FMLMixinService;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.locators.GameLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevFolderLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevJarLocator;
import net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.startup.FatalStartupException;
import net.neoforged.fml.startup.StartupArgs;
import net.neoforged.fml.util.ClasspathResourceUtils;
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
import org.spongepowered.asm.service.MixinService;

public final class FMLLoader implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicReference<@Nullable FMLLoader> current = new AtomicReference<>();

    /**
     * The context class-loader that will be restored when the loader is closed.
     */
    @Nullable
    private final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    /**
     * The current tail of the class-loader chain. It is moved whenever a new set of Jars is loaded.
     */
    private ClassLoader currentClassLoader;
    /**
     * Resources owned by this loader, such as opened URL classloaders.
     */
    private final List<AutoCloseable> ownedResources = new ArrayList<>();
    private final ProgramArgs programArgs;
    private final Dist dist;
    private final boolean production;
    private final Path gameDir;
    private final Path cacheDir;
    private VersionInfo versionInfo;
    @Nullable
    private ModuleLayer gameLayer;
    /**
     * Used to track where Jar files we extract to disk originally came from. Used for error reporting.
     */
    private final Map<Path, String> jarSourceInfo = new HashMap<>();
    private final Set<Path> locatedPaths = new HashSet<>();
    private final List<File> unclaimedClassPathEntries = new ArrayList<>();
    private AccessTransformerEngine accessTransformer;
    private LanguageProviderLoader languageProviderLoader;
    private LoadingModList loadingModList;
    // NOTE: NeoForge patches reference this field directly, sadly.
    public static final Runnable progressWindowTick = ImmediateWindowHandler::renderTick;
    public BackgroundScanHandler backgroundScanHandler;
    @VisibleForTesting
    DiscoveryResult discoveryResult;

    @VisibleForTesting
    record DiscoveryResult(List<ModFile> pluginContent,
            List<ModFile> gameContent,
            List<ModLoadingIssue> discoveryIssues) {}

    private FMLLoader(ClassLoader currentClassLoader, String[] programArgs, Dist dist, boolean production, Path gameDir, Path cacheDir) {
        this.currentClassLoader = currentClassLoader;
        this.programArgs = ProgramArgs.from(programArgs);
        this.dist = dist;
        this.production = production;
        this.gameDir = gameDir;
        this.cacheDir = cacheDir;

        versionInfo = new VersionInfo(
                this.programArgs.remove("fml.neoForgeVersion"),
                FMLVersion.getVersion(),
                this.programArgs.remove("fml.mcVersion"),
                this.programArgs.remove("fml.neoFormVersion"));

        LOGGER.info(
                "Starting FancyModLoader version {} ({} in {})",
                FMLVersion.getVersion(),
                dist,
                production ? "PROD" : "DEV");

        makeCurrent();
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    private static Dist detectDist(ClassLoader classLoader) {
        var clientAvailable = classLoader.getResource("net/minecraft/client/main/Main.class") != null;
        return clientAvailable ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    private static boolean detectProduction(ClassLoader classLoader) {
        // We are not in production when an unobfuscated class is reachable on the classloader
        // since that means the unobfuscated game is on the classpath. We use DetectedVersion here since
        // it has existed across many Minecraft versions.
        return classLoader.getResource("net/minecraft/DetectedVersion.class") == null;
    }

    @Override
    public void close() {
        LOGGER.info("Closing FML Loader {}", this);
        if (this == current.compareAndExchange(this, null)) {
            // Clean up some further shared state
            ModList.clear();
            ModLoader.clear();
            // The bytecode provider holds a static global strong reference to the entire class-loader chain
            // which will keep JAR files opened.
            ((FMLMixinService) MixinService.getService()).setBytecodeProvider(null);
        }

        for (var ownedResource : ownedResources) {
            try {
                ownedResource.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close resource {} owned by FMLLoader", ownedResource, e);
            }
        }
        ownedResources.clear();

        if (Thread.currentThread().getContextClassLoader() == currentClassLoader) {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void makeCurrent() {
        FMLLoader witness = current.compareAndExchange(null, this);
        if (witness != null) {
            throw new IllegalStateException("Another FML loader is already active: " + witness);
        }
    }

    public ClassLoader currentClassLoader() {
        return currentClassLoader;
    }

    public ProgramArgs programArgs() {
        return programArgs;
    }

    public static FMLLoader create(@Nullable Instrumentation instrumentation, StartupArgs startupArgs) {
        // If a client class is available, then it's client, otherwise DEDICATED_SERVER
        // The auto-detection never detects JOINED since it's impossible to do so
        var initialLoader = Objects.requireNonNullElse(startupArgs.parentClassLoader(), ClassLoader.getSystemClassLoader());

        var loader = new FMLLoader(
                initialLoader,
                startupArgs.programArgs(),
                Objects.requireNonNullElseGet(startupArgs.dist(), () -> detectDist(initialLoader)),
                detectProduction(initialLoader),
                startupArgs.gameDirectory(),
                startupArgs.cacheRoot());

        try {
            FMLPaths.loadAbsolutePaths(startupArgs.gameDirectory());
            FMLConfig.load();

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

            var launchContext = loader.new LaunchContextAdapter();
            for (var claimedFile : startupArgs.claimedFiles()) {
                launchContext.addLocated(claimedFile.toPath());
            }

            loader.loadEarlyServices();

            ImmediateWindowHandler.load(startupArgs.headless(), loader.programArgs);

            var mixinFacade = new MixinFacade();

            // Add our own launch plugins explicitly. These do need to exist before mod discovery,
            // as mod discovery will add its results to these engines directly.
            loader.accessTransformer = addLaunchPlugin(launchContext, launchPlugins, new AccessTransformerService()).engine;
            addLaunchPlugin(launchContext, launchPlugins, new RuntimeEnumExtender());
            if (startupArgs.cleanDist()) {
                addLaunchPlugin(launchContext, launchPlugins, new RuntimeDistCleaner(loader.dist));
            }
            addLaunchPlugin(launchContext, launchPlugins, mixinFacade.getLaunchPlugin());

            DiscoveryResult discoveryResult;
            if (startupArgs.headless()) {
                discoveryResult = loader.runDiscovery();
            } else {
                discoveryResult = runOffThread(loader::runDiscovery);
            }
            ClassLoadingGuardian classLoadingGuardian = null;
            if (instrumentation != null) {
                classLoadingGuardian = new ClassLoadingGuardian(instrumentation, discoveryResult.gameContent);
                loader.ownedResources.add(classLoadingGuardian);
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
            loader.loadPlugins(loader.loadingModList.getPlugins());

            // Now go and build the language providers and let mods discover theirs
            loader.languageProviderLoader = new LanguageProviderLoader(launchContext);
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

            var classTransformer = ClassTransformerFactory.create(launchContext, launchPluginHandler, loader.loadingModList);
            loader.ownedResources.add(new ClassTransformerProfiler(classTransformer));
            var transformingLoader = loader.buildTransformingLoader(classTransformer, gameContent);

            // From here on out, try loading through the TCL
            if (classLoadingGuardian != null) {
                classLoadingGuardian.setAllowedClassLoader(transformingLoader);
            }

            // We're adding mixins *after* setting the Thread context classloader since
            // Mixin stubbornly loads Mixin Configs via its ModLauncher environment using the TCL.
            // Adding containers beforehand will try to load Mixin configs using the app classloader and fail.
            mixinFacade.finishInitialization(loader.loadingModList, transformingLoader);

            // This will initialize Mixins, for example
            launchPluginHandler.announceLaunch(transformingLoader, new NamedPath[0]);

            ImmediateWindowHandler.acceptGameLayer(loader.gameLayer);
            ImmediateWindowHandler.updateProgress("Launching minecraft");
            loader.progressWindowTick.run();

            return loader;
        } catch (RuntimeException | Error e) {
            loader.close();
            throw e;
        }
    }

    private void loadEarlyServices() {
        // Search for early services
        var earlyServices = EarlyServiceDiscovery.findEarlyServices(FMLPaths.MODSDIR.get());
        if (!earlyServices.isEmpty()) {
            // TODO: Early services should participate in JIJ discovery
            appendLoader("FML Early Services", earlyServices);
        }
    }

    private void loadPlugins(List<IModFileInfo> plugins) {
        List<Path> pluginPaths = new ArrayList<>(plugins.size());
        for (var plugin : plugins) {
            var jar = plugin.getFile().getSecureJar();

            unwrapSecureJar(formatModFileLocation(plugin.getFile()), jar, pluginPaths::add);
        }

        appendLoader("FML Plugins", pluginPaths);
    }

    private void unwrapSecureJar(String sourceDescription, SecureJar jar, Consumer<Path> sink) {
        var basePaths = getBasePaths(jar, false);

        for (var basePath : basePaths) {
            if (basePath.getFileSystem().provider().getScheme().equals("file")) {
                sink.accept(basePath);
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
                    } catch (IOException ignored) {}
                    if (existingSize != expectedSize) {
                        // TODO atomic move crap
                        Files.write(cachedFile, jarInMemory);
                    }

                    setJarSourceDescription(cachedFile, sourceDescription);
                    sink.accept(cachedFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Loads the given services into a URL classloader.
     */
    private void appendLoader(String loaderName, List<Path> paths) {
        if (paths.isEmpty()) {
            LOGGER.info("No additional classpath items for {} were found.", loaderName);
            return;
        }

        LOGGER.info("Loading {}:", loaderName);

        List<URL> rootUrls = new ArrayList<>(paths.size());
        for (var path : paths) {
            LOGGER.info(" - {}", formatPath(path));
            try {
                rootUrls.add(path.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e); // This should not happen for file URLs
            }
            locatedPaths.add(path); // Prevents it from getting picked up again
        }

        var loader = new URLClassLoader(loaderName, rootUrls.toArray(URL[]::new), currentClassLoader);
        ownedResources.add(loader);
        currentClassLoader = loader;
        Thread.currentThread().setContextClassLoader(loader);
    }

    private String formatModFileLocation(IModFile file) {
        var info = formatPath(file.getFilePath());

        var parentFile = file.getDiscoveryAttributes().parent();
        if (parentFile != null) {
            info = formatModFileLocation(parentFile) + " > " + info;
        }

        return info;
    }

    private static List<Path> getBasePaths(SecureJar jar, boolean ignoreFilter) {
        if (jar instanceof VirtualJar) {
            return List.of(); // virtual jars have no real paths
        }

        var unionFs = (UnionFileSystem) jar.getRootPath().getFileSystem();
        if (!ignoreFilter && unionFs.getFilesystemFilter() != null) {
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

    private TransformingClassLoader buildTransformingLoader(ClassTransformer classTransformer,
            List<SecureJar> content) {
        maskContentAlreadyOnClasspath(content);

        long start = System.currentTimeMillis();

        var parentLayers = List.of(ModuleLayer.boot());

        var cf = Configuration.resolveAndBind(
                JarModuleFinder.of(content.toArray(SecureJar[]::new)),
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                content.stream().map(SecureJar::name).toList());

        var moduleNames = getModuleNameList(cf);
        LOGGER.info("Building module layer GAME:\n{}", moduleNames);
        var loader = new TransformingClassLoader(classTransformer, cf, parentLayers, currentClassLoader);

        var layer = ModuleLayer.defineModules(
                cf,
                parentLayers,
                f -> loader).layer();

        var elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Built game layer in {}ms", elapsed);

        loader.setFallbackClassLoader(currentClassLoader);

        gameLayer = layer;
        currentClassLoader = loader;
        Thread.currentThread().setContextClassLoader(loader);
        return loader;
    }

    /**
     * If any location being added is already on the classpath, we add a masking classloader to ensure
     * that resources are not double-reported when using getResources/getResource.
     *
     * The primary purpose of this is in mod and NeoForge development environments, where IDEs put the mod
     * on the app classpath, but we also add it as content to the game layer. This method is responsible
     * for setting up a classloader that prevents getResource/getResources from reporting Jar resources
     * for both the jar on the App classpath and on the transforming classloader.
     */
    private void maskContentAlreadyOnClasspath(List<SecureJar> content) {
        var classpathItems = ClasspathResourceUtils.getAllClasspathItems(currentClassLoader);

        // Collect all paths that make up the game content, which are already on the classpath
        Set<Path> needsMasking = new HashSet<>();
        for (var secureJar : content) {
            for (var basePath : getBasePaths(secureJar, true)) {
                if (classpathItems.contains(basePath)) {
                    needsMasking.add(basePath);
                }
            }
        }

        if (!needsMasking.isEmpty()) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Masking classpath elements: {}", needsMasking.stream().map(this::formatPath).toList());
            }

            var maskedLoader = new ResourceMaskingClassLoader(currentClassLoader, needsMasking);
            if (Thread.currentThread().getContextClassLoader() == currentClassLoader) {
                Thread.currentThread().setContextClassLoader(maskedLoader);
            }
            currentClassLoader = maskedLoader;
        }
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

    private DiscoveryResult runDiscovery() {
        var progress = StartupNotificationManager.prependProgressBar("Discovering mods...", 0);

        var additionalLocators = new ArrayList<IModFileCandidateLocator>();

        additionalLocators.add(new GameLocator());
        additionalLocators.add(new InDevFolderLocator());
        additionalLocators.add(new InDevJarLocator());
        additionalLocators.add(new ModsFolderLocator());

        var modDiscoverer = new ModDiscoverer(new LaunchContextAdapter(), additionalLocators);
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
                addAccessTransformer(accessTransformer, modFile);
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

        return this.discoveryResult = new DiscoveryResult(
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

    public static LanguageProviderLoader getLanguageLoadingProvider() {
        return current().languageProviderLoader;
    }

    private void addAccessTransformer(Path atPath, ModFile modName) {
        LOGGER.debug(LogMarkers.SCAN, "Adding Access Transformer in {}", modName.getFilePath());
        try {
            accessTransformer.loadATFromPath(atPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AT at " + atPath.toAbsolutePath(), e);
        }
    }

    public static FMLLoader current() {
        var current = currentOrNull();
        if (current == null) {
            throw new IllegalStateException("There is no current FML Loader");
        }
        return current;
    }

    @Nullable
    public static FMLLoader currentOrNull() {
        return current.get();
    }

    public static Dist getDist() {
        return current().dist;
    }

    public static LoadingModList getLoadingModList() {
        return current().loadingModList;
    }

    public static Path getGamePath() {
        return current().gameDir;
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
        return current().production;
    }

    public static ModuleLayer getGameLayer() {
        var gameLayer = current().gameLayer;
        if (gameLayer == null) {
            throw new IllegalStateException("This can only be called after mod discovery is completed");
        }
        return gameLayer;
    }

    public static VersionInfo versionInfo() {
        return current().versionInfo;
    }

    public String formatPath(Path path) {
        String resultPath;

        if (path.startsWith(gameDir)) {
            resultPath = gameDir.relativize(path).toString();
        } else if (Files.isDirectory(path)) {
            resultPath = path.toAbsolutePath().toString();
        } else {
            resultPath = path.getFileName().toString();
        }

        // Unify separators to ensure it is easier to test
        resultPath = resultPath.replace('\\', '/');

        var originalSource = jarSourceInfo.get(path);
        if (originalSource != null) {
            resultPath = originalSource + " (at " + resultPath + ")";
        }

        return resultPath;
    }

    private void setJarSourceDescription(Path path, String description) {
        jarSourceInfo.put(path, description);
    }

    private String getJarSourceDescription(Path path) {
        return jarSourceInfo.get(path);
    }

    class LaunchContextAdapter implements ILaunchContext {
        @Override
        public Dist getRequiredDistribution() {
            return dist;
        }

        @Override
        public Path gameDirectory() {
            return gameDir;
        }

        @Override
        public <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass) {
            // We simply rely on thread context classloader to be correct
            return ServiceLoader.load(serviceClass).stream();
        }

        @Override
        public boolean isLocated(Path path) {
            return FMLLoader.this.locatedPaths.contains(path);
        }

        @Override
        public boolean addLocated(Path path) {
            return FMLLoader.this.locatedPaths.add(path);
        }

        @Override
        public List<File> getUnclaimedClassPathEntries() {
            return unclaimedClassPathEntries.stream()
                    .filter(p -> !isLocated(p.toPath()))
                    .toList();
        }

        @Override
        public void setJarSourceDescription(Path path, String description) {
            FMLLoader.this.setJarSourceDescription(path, description);
        }

        @Override
        public @Nullable String getJarSourceDescription(Path path) {
            return FMLLoader.this.getJarSourceDescription(path);
        }

        @Override
        public String relativizePath(Path path) {
            return FMLLoader.this.formatPath(path);
        }
    }
}
