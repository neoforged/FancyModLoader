/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.TransformingClassLoader;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fmlstartup.api.StartupArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoSettings;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@MockitoSettings
public abstract class LauncherTest {
    protected SimulatedInstallation installation;

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
//        var launchPlugins = new HashMap<String, ILaunchPluginService>();
//        var launchHandlers = new HashMap<String, ILaunchHandlerService>();
//        var environment = new Environment(
//                s -> Optional.ofNullable(launchPlugins.get(s)),
//                s -> Optional.ofNullable(launchHandlers.get(s)),
//                moduleLayerManager);
//        launcher = new Launcher(
//                null,
//                environment,
//                null,
//                null,
//                null,
//                moduleLayerManager
//        );
//        Launcher.INSTANCE = launcher;

        installation = new SimulatedInstallation();
//
//        environment.computePropertyIfAbsent(IEnvironment.Keys.GAMEDIR.get(), ignored -> installation.getGameDir());
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
            // launch represents the modlauncher portion
            LaunchResult result;
            try {
                result = launch(launchTarget, additionalClassPath);
            } catch (Exception e) {
                throw new LaunchException(e);
            }
            // while loadMods is usually triggered from NeoForge
            loadMods();
            return result;
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
        loadMods();
        return result;
    }

    private LaunchResult launch(String launchTarget) throws Exception {
        return launch(launchTarget, List.of());
    }

    private LaunchResult launch(String launchTarget, List<Path> additionalClassPath) {
        ModLoader.clearLoadingIssues();

        System.setProperty("fml.earlyWindowControl", "false");

        var classLoader = Thread.currentThread().getContextClassLoader();
        var startupArgs = new StartupArgs(
                installation.getGameDir().toFile(),
                launchTarget,
                new String[]{
                        "--fml.fmlVersion", SimulatedInstallation.FML_VERSION,
                        "--fml.mcVersion", SimulatedInstallation.MC_VERSION,
                        "--fml.neoForgeVersion", SimulatedInstallation.NEOFORGE_VERSION,
                        "--fml.neoFormVersion", SimulatedInstallation.NEOFORM_VERSION
                },
                locatedPaths.stream().map(Path::toFile).collect(Collectors.toSet()),
                additionalClassPath.stream().map(Path::toFile).toList(),
                true,
                classLoader
        );

        ClassLoader launchClassLoader;
        try {
            var instrumentation = ByteBuddyAgent.install();
            FMLLoader.startup(
                    instrumentation,
                    startupArgs
            );
            launchClassLoader = Thread.currentThread().getContextClassLoader();
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
//
//        // ML would usually handle these two arguments
//        environment.computePropertyIfAbsent(IEnvironment.Keys.GAMEDIR.get(), ignored -> installation.getGameDir());
//        environment.computePropertyIfAbsent(IEnvironment.Keys.LAUNCHTARGET.get(), ignored -> launchTarget);
//
//        createModuleLayer(IModuleLayerManager.Layer.SERVICE, List.of());
//
//        serviceProvider.onLoad(environment, Set.of());
//
//        OptionParser parser = new OptionParser();
//        serviceProvider.arguments((a, b) -> parser.accepts(serviceProvider.name() + "." + a, b));
//        var result = parser.parse(
//                "--fml.fmlVersion", SimulatedInstallation.FML_VERSION,
//                "--fml.mcVersion", SimulatedInstallation.MC_VERSION,
//                "--fml.neoForgeVersion", SimulatedInstallation.NEOFORGE_VERSION,
//                "--fml.neoFormVersion", SimulatedInstallation.NEOFORM_VERSION);
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
        var loadingModList = LoadingModList.get();
        var loadedMods = loadingModList.getModFiles();
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

        // Wait for background scans of all mods to complete
        for (var modFile : loadingModList.getModFiles()) {
            modFile.getFile().getScanResult();
        }

        var discoveryResult = FMLLoader.discoveryResult;

        return new LaunchResult(
                discoveryResult.pluginContent().stream().collect(
                        Collectors.toMap(
                                mf -> mf.getModFileInfo().moduleName(),
                                mf -> mf)),
                discoveryResult.gameContent().stream().collect(
                        Collectors.toMap(
                                mf -> mf.getModFileInfo().moduleName(),
                                mf -> mf)),
                loadingModList.getModLoadingIssues(),
                loadedMods.stream().collect(Collectors.toMap(
                        o -> o.getMods().getFirst().getModId(),
                        o -> o)),
                List.of(),
                launchClassLoader
        );
    }

    private void loadMods() {
        FMLLoader.progressWindowTick = () -> {
        };

        gameClassLoader = FMLLoader.gameClassLoader;

        ModLoader.gatherAndInitializeMods(
                Runnable::run,
                Runnable::run,
                () -> {
                });
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
