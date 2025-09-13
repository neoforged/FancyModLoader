/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarResource;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.CompositeJarContents;
import cpw.mods.jarhandling.impl.EmptyJarContents;
import cpw.mods.jarhandling.impl.FolderJarContents;
import cpw.mods.jarhandling.impl.JarFileContents;
import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
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
import java.util.stream.Stream;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.FMLVersion;
import net.neoforged.fml.IBindingsProvider;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.classloading.ResourceMaskingClassLoader;
import net.neoforged.fml.common.asm.AccessTransformerService;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.jfr.ClassTransformerProfiler;
import net.neoforged.fml.loading.mixin.FMLMixinService;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.loading.moddiscovery.ModDiscoverer;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.locators.GameLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevFolderLocator;
import net.neoforged.fml.loading.moddiscovery.locators.InDevJarLocator;
import net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevDistCleaner;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.startup.FatalStartupException;
import net.neoforged.fml.startup.FmlInstrumentation;
import net.neoforged.fml.startup.StartupArgs;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.fml.util.PathPrettyPrinting;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.ApiStatus;
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
     * Resources owned by this loader, such as opened URL classloaders, which
     * will be closed alongside the loader.
     */
    private final List<AutoCloseable> ownedResources = new ArrayList<>();
    private final ProgramArgs programArgs;

    private LanguageProviderLoader languageProviderLoader;
    private final Dist dist;
    private LoadingModList loadingModList;
    private final Path gameDir;
    private final Set<Path> locatedPaths = new HashSet<>();
    private final List<File> unclaimedClassPathEntries = new ArrayList<>();

    private VersionInfo versionInfo;
    public BackgroundScanHandler backgroundScanHandler;
    private final boolean production;
    @Nullable
    private ModuleLayer gameLayer;
    @VisibleForTesting
    DiscoveryResult discoveryResult;
    private ClassTransformer classTransformer;

    // TODO Make sure this isn't static
    @Nullable
    static volatile IBindingsProvider bindings;

    @VisibleForTesting
    record DiscoveryResult(
            List<ModFile> pluginContent,
            List<ModFile> gameContent,
            List<ModFile> gameLibraryContent,
            List<ModLoadingIssue> discoveryIssues) {}

    private FMLLoader(ClassLoader currentClassLoader, String[] programArgs, Dist dist, boolean production, Path gameDir) {
        this.currentClassLoader = currentClassLoader;
        this.programArgs = ProgramArgs.from(programArgs);
        this.dist = dist;
        this.production = production;
        this.gameDir = gameDir;

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

    @Override
    public void close() {
        LOGGER.info("Closing FML Loader {}", Integer.toHexString(System.identityHashCode(this)));
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

    ClassTransformer getClassTransformer() {
        return classTransformer;
    }

    public static FMLLoader create(StartupArgs startupArgs) {
        var instrumentation = FmlInstrumentation.obtainInstrumentation();
        return create(instrumentation, startupArgs);
    }

    public static FMLLoader create(@Nullable Instrumentation instrumentation, StartupArgs startupArgs) {
        // If a client class is available, then it's client, otherwise DEDICATED_SERVER
        // The auto-detection never detects JOINED since it's impossible to do so
        var initialLoader = Objects.requireNonNullElse(startupArgs.parentClassLoader(), ClassLoader.getSystemClassLoader());

        PathPrettyPrinting.addRoot(startupArgs.gameDirectory());

        var loader = new FMLLoader(
                initialLoader,
                startupArgs.programArgs(),
                Objects.requireNonNullElseGet(startupArgs.dist(), () -> detectDist(initialLoader)),
                detectProduction(initialLoader),
                startupArgs.gameDirectory());

        try {
            FMLPaths.loadAbsolutePaths(startupArgs.gameDirectory());
            FMLConfig.load();

            var launchContext = loader.new LaunchContextAdapter();
            for (var claimedFile : startupArgs.claimedFiles()) {
                launchContext.addLocated(claimedFile.toPath());
            }

            loader.loadEarlyServices();

            ImmediateWindowHandler.load(startupArgs.headless(), loader.programArgs);

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

            var mixinFacade = new MixinFacade();

            var launchPlugins = createLaunchPlugins(startupArgs, launchContext, discoveryResult, mixinFacade);

            var launchPluginHandler = new LaunchPluginHandler(launchPlugins.values().stream());

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
            for (var modFile : discoveryResult.gameLibraryContent) {
                gameContent.add(modFile.getSecureJar());
            }
            for (var modFile : discoveryResult.gameContent) {
                gameContent.add(modFile.getSecureJar());
            }
            // Add generated code container for Mixin
            // TODO: This needs a new API for plugins to contribute synthetic modules, *OR* it should go into the mod-file discovery!
            gameContent.add(mixinFacade.createGeneratedCodeContainer());
            launchPluginHandler.offerScanResultsToPlugins(gameContent);

            loader.classTransformer = ClassTransformerFactory.create(launchContext, launchPluginHandler);
            loader.ownedResources.add(new ClassTransformerProfiler(loader.classTransformer));
            var transformingLoader = loader.buildTransformingLoader(loader.classTransformer, gameContent);

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

            ImmediateWindowHandler.updateProgress("Launching minecraft");
            ImmediateWindowHandler.renderTick();

            return loader;
        } catch (RuntimeException | Error e) {
            loader.close();
            throw e;
        }
    }

    private static Map<String, ILaunchPluginService> createLaunchPlugins(StartupArgs startupArgs,
            LaunchContextAdapter launchContext,
            DiscoveryResult discoveryResult,
            MixinFacade mixinFacade) {
        // Add our own launch plugins explicitly.
        var builtInPlugins = new ArrayList<ILaunchPluginService>();
        builtInPlugins.add(createAccessTransformerService(discoveryResult));
        builtInPlugins.add(new RuntimeEnumExtender());

        if (startupArgs.cleanDist()) {
            var minecraftModFile = discoveryResult.gameContent().stream()
                    .filter(mf -> mf.getId().equals("minecraft"))
                    .findFirst()
                    .map(ModFile::getContents)
                    .orElse(null);
            if (minecraftModFile != null && NeoForgeDevDistCleaner.supportsDistCleaning(minecraftModFile)) {
                builtInPlugins.add(new NeoForgeDevDistCleaner(minecraftModFile, startupArgs.dist()));
            }
        }

        builtInPlugins.add(mixinFacade.getLaunchPlugin());

        // Discover third party launch plugins
        var result = new HashMap<String, ILaunchPluginService>();
        for (var launchPlugin : ServiceLoaderUtil.loadServices(
                launchContext,
                ILaunchPluginService.class,
                builtInPlugins,
                FMLLoader::isValidLaunchPlugin)) {
            LOGGER.debug("Adding launch plugin {}", launchPlugin.name());
            var previous = result.put(launchPlugin.name(), launchPlugin);
            if (previous != null) {
                var source1 = ServiceLoaderUtil.identifySourcePath(launchContext, launchPlugin);
                var source2 = ServiceLoaderUtil.identifySourcePath(launchContext, previous);

                throw new FatalStartupException("Multiple launch plugin services of the same name '"
                        + previous.name() + "' are present: " + source1 + " and " + source2);
            }
        }
        return result;
    }

    private static ILaunchPluginService createAccessTransformerService(DiscoveryResult discoveryResult) {
        var engine = AccessTransformerEngine.newEngine();
        for (var modFile : discoveryResult.gameContent()) {
            for (var atPath : modFile.getAccessTransformers()) {
                LOGGER.debug(LogMarkers.SCAN, "Adding Access Transformer {} in {}", atPath, modFile);
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

        var moduleNames = getModuleNameList(cf, content);
        LOGGER.info("Building game content classloader:\n{}", moduleNames);
        var loader = new TransformingClassLoader(classTransformer, cf, parentLayers, currentClassLoader);

        var layer = ModuleLayer.defineModules(
                cf,
                parentLayers,
                f -> loader).layer();

        var elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Built game content classloader in {}ms", elapsed);

        loader.setFallbackClassLoader(currentClassLoader);

        gameLayer = layer;
        currentClassLoader = loader;
        Thread.currentThread().setContextClassLoader(loader);
        return loader;
    }

    /**
     * If any location being added is already on the classpath, we add a masking classloader to ensure
     * that resources are not double-reported when using getResources/getResource.
     * <p>
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
            for (var basePath : getBasePaths(secureJar.contents(), true)) {
                if (classpathItems.contains(basePath)) {
                    needsMasking.add(basePath);
                }
            }
        }

        if (!needsMasking.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Masking classpath elements: {}", needsMasking.stream().map(PathPrettyPrinting::prettyPrint).toList());
            }

            var maskedLoader = new ResourceMaskingClassLoader(currentClassLoader, needsMasking);
            if (Thread.currentThread().getContextClassLoader() == currentClassLoader) {
                Thread.currentThread().setContextClassLoader(maskedLoader);
            }
            currentClassLoader = maskedLoader;
        }
    }

    private static List<Path> getBasePaths(JarContents contents, boolean ignoreFilter) {
        var result = new ArrayList<Path>();
        switch (contents) {
            case CompositeJarContents compositeModContainer -> {
                if (!ignoreFilter && compositeModContainer.isFiltered()) {
                    throw new IllegalStateException("Cannot load filtered Jar content into a URL classloader");
                }
                for (var delegate : compositeModContainer.getDelegates()) {
                    result.addAll(getBasePaths(delegate, ignoreFilter));
                }
            }
            case EmptyJarContents ignored -> {}
            case FolderJarContents folderModContainer -> result.add(folderModContainer.getPrimaryPath());
            case JarFileContents jarModContainer -> result.add(jarModContainer.getPrimaryPath());
            default -> throw new IllegalStateException("Don't know how to handle " + contents);
        }
        return result;
    }

    private static String getModuleNameList(Configuration cf, List<SecureJar> content) {
        var jarsById = content.stream().collect(Collectors.toMap(SecureJar::name, Function.identity()));

        return cf.modules().stream()
                .map(module -> {
                    var jar = jarsById.get(module.name());
                    var contentList = jar != null ? jar.contents() : module.reference().location().map(URI::toString).orElse("unknown");
                    return " - " + module.name() + " (" + contentList + ")";
                })
                .sorted()
                .collect(Collectors.joining("\n"));
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

    private void loadEarlyServices() {
        // Search for early services
        var earlyServices = EarlyServiceDiscovery.findEarlyServices(FMLPaths.MODSDIR.get());
        if (!earlyServices.isEmpty()) {
            // TODO: Early services should participate in JIJ discovery
            // TODO: Don't like this flow, the discovery should return the JarContents directly to reuse the JarFile
            var earlyServiceJars = earlyServices.stream().map(path -> {
                try {
                    return JarContents.ofPath(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to open early service jar " + path, e);
                }
            }).toList();
            appendLoader("FML Early Services", earlyServiceJars);
        }
    }

    private void loadPlugins(List<IModFileInfo> plugins) {
        appendLoader("FML Plugins", plugins.stream().map(mfi -> mfi.getFile().getContents()).toList());
    }

    /**
     * Loads the given services into a URL classloader.
     */
    private void appendLoader(String loaderName, List<JarContents> jars) {
        if (jars.isEmpty()) {
            LOGGER.info("No additional classpath items for {} were found.", loaderName);
            return;
        }

        LOGGER.info("Loading {}:", loaderName);

        List<URL> rootUrls = new ArrayList<>(jars.size());
        for (var jar : jars) {
            if (jar instanceof CompositeJarContents compositeJarContents && compositeJarContents.isFiltered()) {
                throw new IllegalArgumentException("Cannot use simple URLClassLoader for filtered content " + jar);
            }

            // TODO: Order on the classpath matters, we need to double-check the content roots are in the right order here
            for (var contentRoot : jar.getContentRoots()) {
                LOGGER.info(" - {}", PathPrettyPrinting.prettyPrint(contentRoot));
                try {
                    rootUrls.add(contentRoot.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e); // This should not happen for file URLs
                }
                locatedPaths.add(contentRoot); // Prevents it from getting picked up again
            }
        }

        var loader = new URLClassLoader(loaderName, rootUrls.toArray(URL[]::new), currentClassLoader);
        ownedResources.add(loader);
        currentClassLoader = loader;
        Thread.currentThread().setContextClassLoader(loader);
    }

    private static <T extends ILaunchPluginService> boolean isValidLaunchPlugin(Class<T> serviceClass) {
        // Blacklist any plugins we add ourselves
        if (serviceClass == AccessTransformerService.class) {
            return false;
        }

        // Blacklist all Mixin services, since we implement all of them ourselves
        return !MixinFacade.isMixinServiceClass(serviceClass);
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
        var neoForgeVersion = versionInfo.neoForgeVersion();
        var minecraftVersion = versionInfo.mcVersion();
        for (var modFile : discoveryResult.modFiles()) {
            var mods = modFile.getModFileInfo().getMods();
            if (mods.isEmpty()) {
                continue;
            }
            var mainMod = mods.getFirst();
            switch (modFile.getId()) {
                case "minecraft" -> minecraftVersion = mainMod.getVersion().toString();
                case "neoforge" -> neoForgeVersion = mainMod.getVersion().toString();
            }
        }
        versionInfo = new VersionInfo(
                neoForgeVersion,
                versionInfo().fmlVersion(),
                minecraftVersion,
                versionInfo().neoFormVersion());

        progress.complete();

        loadingModList = ModSorter.sort(discoveryResult.modFiles(), issues);

        backgroundScanHandler = new BackgroundScanHandler();
        backgroundScanHandler.setLoadingModList(loadingModList);

        Map<IModInfo, JarResource> enumExtensionsByMod = new HashMap<>();
        for (var modFile : modFiles) {
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

            backgroundScanHandler.submitForScanning(modFile);
        }
        RuntimeEnumExtender.loadEnumPrototypes(enumExtensionsByMod);

        return this.discoveryResult = new DiscoveryResult(
                loadingModList.getPlugins().stream().map(mfi -> (ModFile) mfi.getFile()).toList(),
                loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),
                loadingModList.getGameLibraries().stream().map(mf -> (ModFile) mf).toList(),
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
            // TODO: not really used anymore
            return unclaimedClassPathEntries.stream()
                    .filter(p -> !isLocated(p.toPath()))
                    .toList();
        }

        @Override
        public VersionInfo getVersions() {
            return versionInfo;
        }
    }
}
