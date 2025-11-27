/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.FMLVersion;
import net.neoforged.fml.IBindingsProvider;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.classloading.JarContentsModule;
import net.neoforged.fml.classloading.JarContentsModuleFinder;
import net.neoforged.fml.classloading.transformation.ClassProcessorAuditLog;
import net.neoforged.fml.classloading.transformation.ClassProcessorAuditSource;
import net.neoforged.fml.classloading.transformation.ClassProcessorSet;
import net.neoforged.fml.classloading.transformation.TransformingClassLoader;
import net.neoforged.fml.common.asm.AccessTransformerService;
import net.neoforged.fml.common.asm.SimpleProcessorsGroup;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import net.neoforged.fml.loading.game.GameDiscovery;
import net.neoforged.fml.loading.game.GameDiscoveryResult;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.locators.InDevFolderLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevJarLocator;
import net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevDistCleaner;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.startup.InstrumentationHelper;
import net.neoforged.fml.startup.StartupArgs;
import net.neoforged.fml.util.PathPrettyPrinting;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.LocatedPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public final class FMLLoader implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicReference<@Nullable FMLLoader> current = new AtomicReference<>();

    private final ClassLoaderStack classLoaderStack;

    /**
     * Resources owned by this loader, which will be closed alongside the loader.
     */
    private final List<AutoCloseable> ownedResources = new ArrayList<>();
    /**
     * Additional user-supplied resources that will be closed, before this loader itself is closed.
     */
    private final List<AutoCloseable> closeCallbacks = new ArrayList<>();
    private final ProgramArgs programArgs;

    private LanguageProviderLoader languageProviderLoader;
    private final Dist dist;
    private LoadingModList loadingModList;
    private final Path gameDir;
    private final LocatedPaths locatedPaths;

    private VersionSupportMatrix versionSupportMatrix;
    public BackgroundScanHandler backgroundScanHandler;
    @Nullable
    private ModuleLayer gameLayer;
    private final List<ModFile> earlyServicesJars = new ArrayList<>();
    private final GameDiscoveryResult discoveredGame;
    @VisibleForTesting
    DiscoveryResult discoveryResult;
    private final ClassProcessorAuditLog classTransformerAuditLog = new ClassProcessorAuditLog();
    @Nullable
    @VisibleForTesting
    volatile IBindingsProvider bindings;

    @ApiStatus.Internal
    public ClassProcessorAuditSource getClassTransformerAuditLog() {
        return classTransformerAuditLog;
    }

    @VisibleForTesting
    record DiscoveryResult(
            List<ModFile> pluginContent,
            List<ModFile> gameContent,
            List<ModFile> gameLibraryContent,
            List<ModLoadingIssue> discoveryIssues) {
        public List<ModFile> allContent() {
            var content = new ArrayList<ModFile>(
                    pluginContent.size() + gameContent.size() + gameLibraryContent.size());
            content.addAll(pluginContent);
            content.addAll(gameContent);
            content.addAll(gameLibraryContent);
            return content;
        }

        public List<ModFile> allGameContent() {
            var content = new ArrayList<ModFile>(
                    gameContent.size() + gameLibraryContent.size());
            content.addAll(gameContent);
            content.addAll(gameLibraryContent);
            return content;
        }

        public boolean hasErrors() {
            return discoveryIssues.stream().anyMatch(i -> i.severity() == ModLoadingIssue.Severity.ERROR);
        }
    }

    private FMLLoader(LocatedPaths locatedPaths,
            ClassLoaderStack classLoaderStack,
            GameDiscoveryResult discoveredGame,
            ProgramArgs programArgs,
            Dist dist,
            Path gameDir) {
        this.locatedPaths = locatedPaths;
        this.classLoaderStack = classLoaderStack;
        this.discoveredGame = discoveredGame;
        this.programArgs = programArgs;
        this.dist = dist;
        this.gameDir = gameDir;

        makeCurrent();
    }

    /**
     * Tries to get the mod file that a given class belongs to.
     *
     * @return Null, if the class doesn't belong to a mod file.
     */
    @Nullable
    public IModFile getModFileByClass(Class<?> clazz) {
        var packageName = clazz.getPackageName();
        if (packageName.isEmpty()) {
            return null;
        }
        return loadingModList.getPackageIndex().get(packageName);
    }

    /**
     * Adds a callback that will be called once, when this loader is about to close.
     * <p>Mod files and class-loaders will not be closed yet when the callback is called, allowing for
     * class-loading to occur normally.
     */
    public void addCloseCallback(AutoCloseable callback) {
        closeCallbacks.add(callback);
    }

    @Override
    public void close() {
        LOGGER.info("Closing FML Loader {}", Integer.toHexString(System.identityHashCode(this)));

        for (var closeCallback : closeCallbacks) {
            try {
                closeCallback.close();
            } catch (Exception e) {
                LOGGER.error("Failed to run mod-supplied close callback {}", closeCallback, e);
            }
        }
        closeCallbacks.clear();

        for (var modFile : earlyServicesJars) {
            modFile.close();
        }
        earlyServicesJars.clear();

        if (loadingModList != null) {
            for (var modFile : loadingModList.getModFiles()) {
                modFile.getFile().close();
            }
            for (var modFile : loadingModList.getPlugins()) {
                ((ModFile) modFile.getFile()).close();
            }
            for (var modFile : loadingModList.getGameLibraries()) {
                ((ModFile) modFile).close();
            }
        }
        // Clean up some further shared state
        if (this == current.compareAndExchange(this, null)) {
            ModList.clear();
            ModLoader.clear();
        }

        for (var ownedResource : ownedResources) {
            try {
                ownedResource.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close resource {} owned by FMLLoader", ownedResource, e);
            }
        }
        ownedResources.clear();

        classLoaderStack.close();
    }

    private void makeCurrent() {
        FMLLoader witness = current.compareAndExchange(null, this);
        if (witness != null) {
            throw new IllegalStateException("Another FML loader is already active: " + witness);
        }
    }

    public ClassLoader getCurrentClassLoader() {
        return classLoaderStack.getCurrentClassLoader();
    }

    public ProgramArgs getProgramArgs() {
        return programArgs;
    }

    @ApiStatus.Internal
    public IBindingsProvider getBindings() {
        if (bindings == null) {
            synchronized (this) {
                if (bindings == null) {
                    if (gameLayer == null) {
                        throw new IllegalStateException("Cannot retrieve bindings before the game layer is initialized.");
                    }
                    var providers = ServiceLoader.load(gameLayer, IBindingsProvider.class).stream().toList();
                    if (providers.isEmpty()) {
                        throw new IllegalStateException("Could not find bindings provider");
                    } else if (providers.size() > 1) {
                        String providerList = providers.stream().map(p -> p.type().getName()).collect(Collectors.joining(", "));
                        throw new IllegalStateException("Found more than one bindings provider: " + providerList);
                    }
                    bindings = providers.getFirst().get();
                }
            }
        }
        return bindings;
    }

    public static FMLLoader create(StartupArgs startupArgs) {
        var instrumentation = InstrumentationHelper.obtainInstrumentation();
        return create(instrumentation, startupArgs);
    }

    public static FMLLoader create(@Nullable Instrumentation instrumentation, StartupArgs startupArgs) {
        var initialLoader = Objects.requireNonNullElse(startupArgs.parentClassLoader(), ClassLoader.getSystemClassLoader());
        var dist = Objects.requireNonNullElseGet(startupArgs.dist(), () -> GameDiscovery.detectDist(initialLoader));
        LOGGER.info("Starting FancyModLoader {} ({}) in {}", FMLVersion.getVersion(), dist, startupArgs.gameDirectory());

        PathPrettyPrinting.addRoot(startupArgs.gameDirectory());

        var programArgs = ProgramArgs.from(startupArgs.programArgs());

        FMLPaths.loadAbsolutePaths(startupArgs.gameDirectory());
        FMLConfig.load();

        var locatedPaths = new LocatedPaths() {
            final Set<Path> paths = new HashSet<>(startupArgs.claimedFiles().stream().map(File::toPath).toList());

            @Override
            public boolean isLocated(Path path) {
                return paths.contains(path);
            }

            @Override
            public boolean addLocated(Path path) {
                return paths.add(path);
            }
        };

        var classLoaderStack = new ClassLoaderStack(initialLoader, locatedPaths);

        var earlyServiceJars = loadEarlyServices(classLoaderStack, startupArgs);

        ImmediateWindowHandler.load(locatedPaths, startupArgs.headless(), programArgs);

        var discoveredGame = runLongRunning(startupArgs, () -> GameDiscovery.discoverGame(programArgs, locatedPaths, dist));
        var neoForgeVersion = discoveredGame.neoforge().getModFileInfo().versionString();
        var minecraftVersion = discoveredGame.minecraft().getModFileInfo().versionString();

        LOGGER.info("Discovered NeoForge {} and Minecraft {} ({})", neoForgeVersion, minecraftVersion, discoveredGame.production() ? "production" : "development");
        ImmediateWindowHandler.setNeoForgeVersion(neoForgeVersion);
        ImmediateWindowHandler.setMinecraftVersion(minecraftVersion);

        var loader = new FMLLoader(locatedPaths, classLoaderStack, discoveredGame, programArgs, dist, startupArgs.gameDirectory());
        try {
            loader.earlyServicesJars.addAll(earlyServiceJars);

            var discoveryResult = runLongRunning(startupArgs, loader::runDiscovery);
            for (var issue : discoveryResult.discoveryIssues()) {
                LOGGER.atLevel(issue.severity() == ModLoadingIssue.Severity.ERROR ? Level.ERROR : Level.WARN)
                        .setCause(issue.cause())
                        .log("{}", FMLTranslations.translateIssueEnglish(issue));
            }
            if (discoveryResult.hasErrors()) {
                throw new ModLoadingException(discoveryResult.discoveryIssues);
            }

            // Build all module descriptors in parallel
            discoveryResult.allContent().stream().parallel().forEach(ModFile::getModuleDescriptor);

            ClassLoadingGuardian classLoadingGuardian = null;
            if (instrumentation != null) {
                classLoadingGuardian = new ClassLoadingGuardian(instrumentation, discoveryResult.allGameContent());
                loader.ownedResources.add(classLoadingGuardian);
            }

            var mixinFacade = new MixinFacade();
            loader.ownedResources.add(mixinFacade);

            // Load Plugins
            loader.loadPlugins(loader.loadingModList.getPlugins());

            // Now go and build the language providers and let mods discover theirs
            loader.languageProviderLoader = new LanguageProviderLoader();
            for (var modFile : discoveryResult.gameContent) {
                modFile.identifyLanguage();
            }

            // BUILD GAME LAYER
            var gameContent = new ArrayList<JarContentsModule>();
            for (var modFile : discoveryResult.allGameContent()) {
                gameContent.add(new JarContentsModule(
                        modFile.getContents(),
                        modFile.getModuleDescriptor()));
            }

            var classProcessorSet = createClassProcessorSet(startupArgs, discoveryResult, mixinFacade);
            if (!classProcessorSet.getGeneratedPackages().isEmpty()) {
                var descriptor = ModuleDescriptor.newAutomaticModule(ClassProcessor.GENERATED_PACKAGE_MODULE)
                        .packages(classProcessorSet.getGeneratedPackages())
                        .build();
                gameContent.add(new JarContentsModule(
                        JarContents.empty(Path.of("VirtualJar/" + descriptor.name())),
                        descriptor));
            }
            var transformingLoader = loader.buildTransformingLoader(classProcessorSet, loader.classTransformerAuditLog, gameContent);

            // From here on out, try loading through the TCL
            if (classLoadingGuardian != null) {
                classLoadingGuardian.setAllowedClassLoader(transformingLoader);
            }

            // We're adding mixins *after* setting the Thread context classloader since
            // Mixin stubbornly loads Mixin Configs via its ModLauncher environment using the TCL.
            // Adding containers beforehand will try to load Mixin configs using the app classloader and fail.
            mixinFacade.finishInitialization(loader.loadingModList, transformingLoader);

            ImmediateWindowHandler.updateProgress("Launching minecraft");
            ImmediateWindowHandler.renderTick();

            return loader;
        } catch (RuntimeException | Error e) {
            try {
                loader.close();
            } catch (Throwable t) {
                e.addSuppressed(t);
            }
            throw e;
        }
    }

    private static ClassProcessorSet createClassProcessorSet(StartupArgs startupArgs,
            DiscoveryResult discoveryResult,
            MixinFacade mixinFacade) {
        // Add our own launch plugins explicitly.
        var builtInProcessors = new ArrayList<ClassProcessor>();
        builtInProcessors.add(createAccessTransformerService(discoveryResult));
        builtInProcessors.add(new RuntimeEnumExtender());
        builtInProcessors.add(new SimpleProcessorsGroup());

        if (startupArgs.cleanDist()) {
            var minecraftModFile = discoveryResult.gameContent().stream()
                    .filter(mf -> mf.getId().equals("minecraft"))
                    .findFirst()
                    .map(ModFile::getContents)
                    .orElse(null);
            if (minecraftModFile != null && NeoForgeDevDistCleaner.supportsDistCleaning(minecraftModFile)) {
                builtInProcessors.add(new NeoForgeDevDistCleaner(minecraftModFile, startupArgs.dist()));
            }
        }

        builtInProcessors.add(mixinFacade.getClassProcessor());

        return ClassProcessorSet.builder()
                .markMarker(ClassProcessorIds.SIMPLE_PROCESSORS_GROUP)
                .markMarker(ClassProcessorIds.COMPUTING_FRAMES)
                .addProcessors(ServiceLoaderUtil.loadServices(ClassProcessor.class, builtInProcessors))
                .addProcessorProviders(ServiceLoaderUtil.loadServices(ClassProcessorProvider.class))
                .build();
    }

    private static ClassProcessor createAccessTransformerService(DiscoveryResult discoveryResult) {
        var engine = AccessTransformerEngine.newEngine();
        for (var modFile : discoveryResult.gameContent()) {
            for (var atPath : modFile.getAccessTransformers()) {
                LOGGER.debug("Adding Access Transformer {} in {}", atPath, modFile);
                try (var in = modFile.getContents().openFile(atPath)) {
                    if (in == null) {
                        LOGGER.error(LogMarkers.LOADING, "Access transformer file {} provided by {} does not exist!", atPath, modFile);
                    } else {
                        engine.loadAT(new InputStreamReader(new BufferedInputStream(in)), atPath);
                    }
                } catch (IOException e) {
                    // TODO: Convert to translated issue?
                    throw new RuntimeException("Failed to load AT at " + atPath + " from " + modFile, e);
                }
            }
        }
        return new AccessTransformerService(engine);
    }

    private TransformingClassLoader buildTransformingLoader(ClassProcessorSet classProcessorSet,
            ClassProcessorAuditLog auditTrail,
            List<JarContentsModule> content) {
        classLoaderStack.maskContentAlreadyOnClasspath(content);

        long start = System.currentTimeMillis();

        var parentLayers = List.of(ModuleLayer.boot());

        var cf = Configuration.resolveAndBind(
                new JarContentsModuleFinder(content),
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                content.stream().map(JarContentsModule::moduleName).toList());

        var moduleNames = getModuleNameList(cf, content);
        LOGGER.info("Building game content classloader:\n{}", moduleNames);
        var loader = new TransformingClassLoader(classProcessorSet, auditTrail, cf, parentLayers, getCurrentClassLoader());

        var layer = ModuleLayer.defineModules(
                cf,
                parentLayers,
                f -> loader).layer();

        var elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Built game content classloader in {}ms", elapsed);

        loader.setFallbackClassLoader(getCurrentClassLoader());

        gameLayer = layer;
        ownedResources.add(loader);
        classLoaderStack.append(loader);
        Thread.currentThread().setContextClassLoader(loader);
        return loader;
    }

    private static String getModuleNameList(Configuration cf, List<JarContentsModule> content) {
        var jarsById = content.stream().collect(Collectors.toMap(JarContentsModule::moduleName, Function.identity()));

        return cf.modules().stream()
                .map(module -> {
                    var jar = jarsById.get(module.name());
                    var contentList = jar != null ? jar.contents() : module.reference().location().map(URI::toString).orElse("unknown");
                    return " - " + module.name() + " (" + contentList + ")";
                })
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private static List<ModFile> loadEarlyServices(ClassLoaderStack classLoaderStack, StartupArgs startupArgs) {
        // Search for early services
        var earlyServicesJars = new ArrayList<>(EarlyServiceDiscovery.findEarlyServiceJars(startupArgs, FMLPaths.MODSDIR.get()));
        if (!earlyServicesJars.isEmpty()) {
            classLoaderStack.appendLoader("FML Early Services", earlyServicesJars.stream().map(IModFile::getContents).toList());
        }
        return earlyServicesJars;
    }

    private void loadPlugins(List<IModFileInfo> plugins) {
        classLoaderStack.appendLoader("FML Plugins", plugins.stream().map(mfi -> mfi.getFile().getContents()).toList());
    }

    private DiscoveryResult runDiscovery() {
        var progress = StartupNotificationManager.prependProgressBar("Discovering mods...", 0);

        var additionalLocators = new ArrayList<IModFileCandidateLocator>();

        additionalLocators.add(new InDevFolderLocator());
        additionalLocators.add(new InDevJarLocator());
        additionalLocators.add(new ModsFolderLocator());

        var modDiscoverer = new ModDiscoverer(new LaunchContextAdapter(), discoveredGame, additionalLocators);
        var discoveryResult = modDiscoverer.discoverMods(earlyServicesJars);

        versionSupportMatrix = new VersionSupportMatrix(getMinecraftVersion());
        progress.complete();

        loadingModList = ModSorter.sort(discoveryResult.modFiles(), discoveryResult.discoveryIssues());

        Map<IModInfo, JarResource> enumExtensionsByMod = new HashMap<>();
        for (var modFile : loadingModList.getAllModFiles()) {
            var mods = modFile.getModInfos();

            for (var mod : mods) {
                mod.getConfig().<String>getConfigElement("enumExtensions").ifPresent(file -> {
                    var resource = mod.getOwningFile().getFile().getContents().get(file);
                    if (resource == null) {
                        ModLoader.addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.enumextender.file_not_found", file).withAffectedMod(mod));
                        return;
                    }
                    enumExtensionsByMod.put(mod, resource);
                });
            }
        }
        RuntimeEnumExtender.loadEnumPrototypes(enumExtensionsByMod);

        backgroundScanHandler = new BackgroundScanHandler(loadingModList.getAllModFiles());

        return this.discoveryResult = new DiscoveryResult(
                loadingModList.getPlugins().stream().map(mfi -> (ModFile) mfi.getFile()).toList(),
                loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),
                loadingModList.getGameLibraries().stream().map(mf -> (ModFile) mf).toList(),
                loadingModList.getModLoadingIssues());
    }

    private static <T> T runLongRunning(StartupArgs startupArgs, Supplier<T> supplier) {
        if (startupArgs.headless()) {
            return supplier.get();
        } else {
            return runOffThread(supplier);
        }
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
        return getCurrent().languageProviderLoader;
    }

    public static FMLLoader getCurrent() {
        var current = getCurrentOrNull();
        if (current == null) {
            throw new IllegalStateException("There is no current FML Loader");
        }
        return current;
    }

    @Nullable
    public static FMLLoader getCurrentOrNull() {
        return current.get();
    }

    public Dist getDist() {
        return dist;
    }

    /**
     * @throws IllegalStateException if the loading mod list hasn't been built yet.
     */
    public LoadingModList getLoadingModList() {
        if (loadingModList == null) {
            throw new IllegalStateException("The loading mod list isn't built yet.");
        }
        return loadingModList;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public boolean isProduction() {
        return discoveredGame.production();
    }

    public ModuleLayer getGameLayer() {
        if (gameLayer == null) {
            throw new IllegalStateException("This can only be called after mod discovery is completed");
        }
        return gameLayer;
    }

    public String getMinecraftVersion() {
        return discoveredGame.minecraft().getJarVersion().toString();
    }

    public String getNeoForgeVersion() {
        return discoveredGame.neoforge().getJarVersion().toString();
    }

    VersionSupportMatrix getVersionSupportMatrix() {
        if (versionSupportMatrix == null) {
            throw new IllegalStateException("Mod discovery has not completed yet, versions may not be known.");
        }
        return versionSupportMatrix;
    }

    private class LaunchContextAdapter implements ILaunchContext {
        @Override
        public Dist getRequiredDistribution() {
            return dist;
        }

        @Override
        public Path gameDirectory() {
            return gameDir;
        }

        @Override
        public boolean isLocated(Path path) {
            return locatedPaths.isLocated(path);
        }

        @Override
        public boolean addLocated(Path path) {
            return locatedPaths.addLocated(path);
        }

        @Override
        public String getMinecraftVersion() {
            return FMLLoader.this.getMinecraftVersion();
        }

        @Override
        public String getNeoForgeVersion() {
            return FMLLoader.this.getNeoForgeVersion();
        }
    }
}
