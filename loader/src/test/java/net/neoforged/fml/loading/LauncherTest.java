/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.lang.invoke.MethodHandles;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
public abstract class LauncherTest {
    @Mock
    MockedStatic<ImmediateWindowHandler> immediateWindowHandlerMock;

    protected TestModuleLayerManager moduleLayerManager = new TestModuleLayerManager();

    protected TestEnvironment environment = new TestEnvironment(moduleLayerManager);

    protected SimulatedInstallation installation;

    protected FMLServiceProvider serviceProvider = new FMLServiceProvider();

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Launcher launcher;

    // can be used to mark paths as already being located before, i.e. if they were loaded
    // by the two early ModLoader discovery interfaces ClasspathTransformerDiscoverer
    // and ModDirTransformerDiscoverer, which pick up files like mixin.
    Set<Path> locatedPaths = new HashSet<>();

    protected TransformingClassLoader gameClassLoader;

    @BeforeAll
    static void ensureAddOpensForUnionFs() {
        // We abuse the ByteBuddy agent that Mockito also uses to open java.lang to UnionFS
        var instrumentation = ByteBuddyAgent.install();
        instrumentation.redefineModule(
                MethodHandles.class.getModule(),
                Set.of(),
                Map.of(),
                Map.of("java.lang.invoke", Set.of(LauncherTest.class.getModule())),
                Set.of(),
                Map.of());
    }

    @BeforeEach
    void setUp() throws Exception {
        Launcher.INSTANCE = launcher;
        when(launcher.findLayerManager()).thenReturn(Optional.of(moduleLayerManager));
        var environmentCtor = Environment.class.getDeclaredConstructor(Launcher.class);
        environmentCtor.setAccessible(true);
        var environment = environmentCtor.newInstance(launcher);
        when(launcher.environment()).thenReturn(environment);

        installation = new SimulatedInstallation();

        immediateWindowHandlerMock.when(ImmediateWindowHandler::getGLVersion).thenReturn("4.3");

        environment.computePropertyIfAbsent(IEnvironment.Keys.GAMEDIR.get(), ignored -> installation.getGameDir());
    }

    @AfterEach
    void clearSystemProperties() throws Exception {
        gameClassLoader = null;
        installation.close();
        Launcher.INSTANCE = null;
    }

    protected LaunchResult launchAndLoadInNeoForgeDevEnvironment(String launchTarget) throws Exception {
        var additionalClasspath = installation.setupNeoForgeDevProject();

        return launchAndLoadWithAdditionalClasspath(launchTarget, additionalClasspath);
    }

    protected LaunchResult launchAndLoadWithAdditionalClasspath(String launchTarget, List<Path> additionalClassPath) throws Exception {
        var urls = additionalClassPath.stream().map(path -> {
            try {
                return path.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);

        var previousCl = Thread.currentThread().getContextClassLoader();
        try (var cl = new URLClassLoader(urls, getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(cl);
            return launchAndLoad(launchTarget);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    protected LaunchResult launchAndLoad(String launchTarget) throws Exception {
        // launch represents the modlauncher portion
        LaunchResult result;
        try {
            result = launch(launchTarget);
        } catch (Exception e) {
            throw new LaunchException(e);
        }
        // while loadMods is usually triggered from NeoForge
        loadMods(result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private LaunchResult launch(String launchTarget) throws Exception {
        ModLoader.clearLoadingIssues();

        // ML would usually handle these two arguments
        environment.computePropertyIfAbsent(IEnvironment.Keys.GAMEDIR.get(), ignored -> installation.getGameDir());
        environment.computePropertyIfAbsent(IEnvironment.Keys.LAUNCHTARGET.get(), ignored -> launchTarget);

        createModuleLayer(IModuleLayerManager.Layer.SERVICE, List.of());

        serviceProvider.onLoad(environment, Set.of());

        OptionParser parser = new OptionParser();
        serviceProvider.arguments((a, b) -> parser.accepts(serviceProvider.name() + "." + a, b));
        var result = parser.parse(
                "--fml.fmlVersion", SimulatedInstallation.FML_VERSION,
                "--fml.mcVersion", SimulatedInstallation.MC_VERSION,
                "--fml.neoForgeVersion", SimulatedInstallation.NEOFORGE_VERSION,
                "--fml.neoFormVersion", SimulatedInstallation.NEOFORM_VERSION);
        serviceProvider.argumentValues(new ITransformationService.OptionResult() {
            @Override
            public <V> V value(OptionSpec<V> options) {
                return result.valueOf(options);
            }

            @Override
            public <V> List<V> values(OptionSpec<V> options) {
                return result.valuesOf(options);
            }
        });

        serviceProvider.initialize(environment);

        // We need to redirect the launch context to add services reachable via the system classloader since
        // this unit test and the main code is not loaded in a modular fashion
        assertThat(serviceProvider.launchContext).isNotNull();
        assertSame(environment, serviceProvider.launchContext.environment());
        serviceProvider.launchContext = new TestLaunchContext(serviceProvider.launchContext, locatedPaths);

        var pluginResources = serviceProvider.beginScanning(environment);
        // In this phase, FML should only return plugin libraries
        assertThat(pluginResources).extracting(ITransformationService.Resource::target).containsOnly(IModuleLayerManager.Layer.PLUGIN);
        createModuleLayer(IModuleLayerManager.Layer.PLUGIN, pluginResources.stream().flatMap(resource -> resource.resources().stream()).toList());

        var gameLayerResources = serviceProvider.completeScan(moduleLayerManager);
        // In this phase, FML should only return game layer content
        assertThat(gameLayerResources).extracting(ITransformationService.Resource::target).containsOnly(IModuleLayerManager.Layer.GAME);

        // Query transformers now, which ML does before building the transforming class loader and launching the game
        var transformers = serviceProvider.transformers();

        var loadingModList = LoadingModList.get();
        var loadedMods = loadingModList.getModFiles();

        var pluginSecureJars = pluginResources.stream()
                .flatMap(r -> r.resources().stream())
                .collect(Collectors.toMap(
                        SecureJar::name,
                        Function.identity()));
        var gameSecureJars = gameLayerResources.stream()
                .flatMap(r -> r.resources().stream())
                .collect(Collectors.toMap(
                        SecureJar::name,
                        Function.identity()));

        // Wait for background scans of all mods to complete
        for (var modFile : loadingModList.getModFiles()) {
            modFile.getFile().getScanResult();
        }

        return new LaunchResult(
                pluginSecureJars,
                gameSecureJars,
                loadingModList.getModLoadingIssues(),
                loadedMods.stream().collect(Collectors.toMap(
                        o -> o.getMods().getFirst().getModId(),
                        o -> o)),
                (List<ITransformer<?>>) transformers);
    }

    private void loadMods(LaunchResult launchResult) throws Exception {
        FMLLoader.progressWindowTick = () -> {};

        // build the game layer
        var parents = List.of(ModuleLayer.boot());
        var parentConfigs = parents.stream().map(ModuleLayer::configuration).toList();
        var gameLayerFinder = JarModuleFinder.of(launchResult.gameLayerModules().values().toArray(new SecureJar[0]));
        var configuration = Configuration.resolveAndBind(ModuleFinder.of(), parentConfigs, gameLayerFinder, launchResult.gameLayerModules().keySet());
        /*
         * Does the minimum to get a transforming classloader.
         */
        var transformStore = new TransformStore();
        new TransformationServiceDecorator(serviceProvider).gatherTransformers(transformStore);

        Launcher.INSTANCE.environment().computePropertyIfAbsent(IEnvironment.Keys.MODLIST.get(), ignored1 -> new ArrayList<>());
        var lph = new LaunchPluginHandler(environment.getLaunchPlugins());
        gameClassLoader = new TransformingClassLoader(
                transformStore,
                lph,
                launcher.environment(),
                configuration,
                parents,
                getClass().getClassLoader());

        var controller = ModuleLayer.defineModules(
                configuration,
                parents,
                ignored -> gameClassLoader);
        moduleLayerManager.setLayer(IModuleLayerManager.Layer.BOOT, ModuleLayer.empty());
        moduleLayerManager.setLayer(IModuleLayerManager.Layer.GAME, controller.layer());

        FMLLoader.beforeStart(controller.layer());

        ModLoader.gatherAndInitializeMods(
                Runnable::run,
                Runnable::run,
                () -> {});
    }

    protected static List<String> getTranslatedIssues(LaunchResult launchResult) {
        return getTranslatedIssues(launchResult.issues());
    }

    protected static List<String> getTranslatedIssues(List<ModLoadingIssue> issues) {
        return issues
                .stream()
                .map(issue -> issue.severity() + ": " + sanitizeIssueText(issue))
                .toList();
    }

    private static String sanitizeIssueText(ModLoadingIssue issue) {
        var text = FMLTranslations.stripControlCodes(FMLTranslations.translateIssue(issue));

        // Dynamically generated classnames cannot be asserted against
        text = text.replaceAll("\\$MockitoMock\\$\\w+", "");

        // Normalize path separators so tests run on Linux and Windows
        text = text.replace("\\", "/");

        return text;
    }

    private void createModuleLayer(IModuleLayerManager.Layer layer, Collection<SecureJar> jars) {
        var moduleFinder = JarModuleFinder.of(jars.toArray(SecureJar[]::new));

        var cf = Configuration.resolveAndBind(
                ModuleFinder.of(),
                List.of(ModuleLayer.boot().configuration()),
                moduleFinder,
                moduleFinder.findAll().stream().map(r -> r.descriptor().name()).toList());
        var parentLayers = List.of(ModuleLayer.boot());
        var moduleClassLoader = new ModuleClassLoader(layer.name(), cf, parentLayers, getClass().getClassLoader());
        var moduleLayer = ModuleLayer.defineModules(
                cf,
                parentLayers,
                s -> moduleClassLoader).layer();

        moduleLayerManager.setLayer(layer, moduleLayer);
    }

    protected Map<String, IModFileInfo> getLoadedMods() {
        return ModList.get().getMods().stream()
                .collect(Collectors.toMap(
                        IModInfo::getModId,
                        IModInfo::getOwningFile));
    }

    protected Map<String, IModFileInfo> getLoadedPlugins() {
        return LoadingModList.get().getPlugins().stream()
                .collect(Collectors.toMap(
                        f -> f.getMods().getFirst().getModId(),
                        f -> f));
    }

    /**
     * When an exception occurs during the ModLauncher controller portion of Startup (represented by {@link #launch},
     * that exception will not result in a user-friendly error screen. Those errors should instead - if possible -
     * be recorded in {@link LoadingModList} to then later be reported by {@link ModLoader#gatherAndInitializeMods}.
     */
    public static class LaunchException extends Exception {
        public LaunchException(Throwable cause) {
            super(cause);
        }
    }
}
