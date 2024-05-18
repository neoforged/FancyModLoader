/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModularURLHandler;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.cl.UnionURLStreamHandler;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforgespi.Environment;
import org.junit.jupiter.api.AfterEach;
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

    // can be used to mark paths as already being located before, i.e. if they were loaded
    // by the two early ModLoader discovery interfaces ClasspathTransformerDiscoverer
    // and ModDirTransformerDiscoverer, which pick up files like mixin.
    Set<Path> locatedPaths = new HashSet<>();

    @BeforeEach
    void setUp() throws IOException {
        installation = new SimulatedInstallation();

        immediateWindowHandlerMock.when(ImmediateWindowHandler::getGLVersion).thenReturn("4.3");
    }

    @AfterEach
    void clearSystemProperties() throws Exception {
        installation.close();
    }

    protected LaunchResult launchInNeoForgeDevEnvironment(String launchTarget) throws Exception {
        var additionalClasspath = installation.setupNeoForgeDevProject();

        return launchWithAdditionalClasspath(launchTarget, additionalClasspath);
    }

    protected LaunchResult launchWithAdditionalClasspath(String launchTarget, List<Path> additionalClassPath) throws Exception {
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
            return launch(launchTarget);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    protected LaunchResult launch(String launchTarget) throws Exception {
        environment.computePropertyIfAbsent(IEnvironment.Keys.GAMEDIR.get(), ignored -> installation.getGameDir());
        environment.computePropertyIfAbsent(IEnvironment.Keys.LAUNCHTARGET.get(), ignored -> launchTarget);

        FMLPaths.loadAbsolutePaths(installation.getGameDir());

        FMLLoader.onInitialLoad(environment);
        FMLPaths.setup(environment);
        FMLConfig.load();
        FMLLoader.setupLaunchHandler(environment, new VersionInfo(
                SimulatedInstallation.NEOFORGE_VERSION,
                SimulatedInstallation.FML_VERSION,
                SimulatedInstallation.MC_VERSION,
                SimulatedInstallation.NEOFORM_VERSION));
        FMLEnvironment.setupInteropEnvironment(environment);
        Environment.build(environment);

        var launchContext = new TestLaunchContext(environment, locatedPaths);
        var pluginResources = FMLLoader.beginModScan(launchContext);
        // In this phase, FML should only return plugin libraries
        assertThat(pluginResources).extracting(ITransformationService.Resource::target).containsOnly(IModuleLayerManager.Layer.PLUGIN);

        var gameLayerResources = FMLLoader.completeScan(launchContext, List.of());
        // In this phase, FML should only return game layer content
        assertThat(gameLayerResources).extracting(ITransformationService.Resource::target).containsOnly(IModuleLayerManager.Layer.GAME);

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
                        o -> o)));
    }

    public void loadMods(LaunchResult launchResult) throws Exception {
        assertThat(launchResult.issues()).isEmpty();
        FMLLoader.progressWindowTick = () -> {};

        // build the game layer
        var parents = List.of(ModuleLayer.boot());
        var parentConfigs = parents.stream().map(ModuleLayer::configuration).toList();
        var gameLayerFinder = JarModuleFinder.of(launchResult.gameLayerModules().values().toArray(new SecureJar[0]));
        var configuration = Configuration.resolveAndBind(ModuleFinder.of(), parentConfigs, gameLayerFinder, launchResult.gameLayerModules().keySet());
        var classLoader = new ModuleClassLoader("GAME", configuration, parents, getClass().getClassLoader());
        var controller = ModuleLayer.defineModules(
                configuration,
                parents,
                ignored -> classLoader);

        FMLLoader.beforeStart(controller.layer());

        var handlers = Map.of("union", new UnionURLStreamHandler());
        var handlersField = ModularURLHandler.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        handlersField.set(ModularURLHandler.INSTANCE, handlers);

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
}
