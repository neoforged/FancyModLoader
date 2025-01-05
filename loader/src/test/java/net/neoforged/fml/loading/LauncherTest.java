/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.startup.StartupArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
public abstract class LauncherTest {
    protected SimulatedInstallation installation;

    // can be used to mark paths as already being located before, i.e. if they were loaded
    // by the two early ModLoader discovery interfaces ClasspathTransformerDiscoverer
    // and ModDirTransformerDiscoverer, which pick up files like mixin.
    Set<Path> locatedPaths = new HashSet<>();

    protected FMLLoader loader;

    private final List<AutoCloseable> ownedResources = new ArrayList<>();

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
    final void setUp() throws Exception {
        if (FMLLoader.currentOrNull() != null) {
            throw new IllegalStateException("A previous test leaked an active FMLLoader. These tests will fail.");
        }

        // Clear in case other tests have set it and failed to reset it
        SimulatedInstallation.setModFoldersProperty(Map.of());
        installation = new SimulatedInstallation();
    }

    @AfterEach
    final void cleanupLoaderAndInstallation() throws Exception {
        if (loader != null) {
            loader.close();
            loader = null;
        }
        for (var ownedResource : ownedResources) {
            ownedResource.close();
        }
        ownedResources.clear();

        System.gc(); // A desperate attempt at cleaning up module loaders before trying to delete Jars

        installation.close();
    }

    protected LaunchResult launchAndLoadInNeoForgeDevEnvironment(LaunchMode launchTarget) throws Exception {
        var additionalClasspath = installation.setupNeoForgeDevProject();

        return launchAndLoadWithAdditionalClasspath(launchTarget, additionalClasspath);
    }

    protected LaunchResult launchAndLoadWithAdditionalClasspath(LaunchMode launchTarget, List<Path> additionalClassPath) throws Exception {
        var urls = additionalClassPath.stream().map(path -> {
            try {
                return path.normalize().toUri().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);

        var previousCl = Thread.currentThread().getContextClassLoader();
        var cl = new URLClassLoader(urls, getClass().getClassLoader());
        ownedResources.add(cl);

        try {
            Thread.currentThread().setContextClassLoader(cl);
            // launch represents the modlauncher portion
            var result = launch(launchTarget, additionalClassPath);
            // while loadMods is usually triggered from NeoForge
            loadMods();
            return result;
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    protected LaunchResult launchAndLoad(LaunchMode launchTarget) {
        // launch represents the modlauncher portion
        LaunchResult result = launch(launchTarget);
        // while loadMods is usually triggered from NeoForge
        loadMods();
        return result;
    }

    private LaunchResult launch(LaunchMode launchTarget) {
        return launch(launchTarget, List.of());
    }

    protected enum LaunchMode {
        PROD_CLIENT(Dist.CLIENT),
        PROD_SERVER(Dist.DEDICATED_SERVER),
        DEV_CLIENT(Dist.CLIENT),
        DEV_SERVER(Dist.DEDICATED_SERVER),
        DEV_CLIENT_DATA(Dist.CLIENT),
        DEV_SERVER_DATA(Dist.DEDICATED_SERVER);

        final Dist forcedDist;

        LaunchMode(Dist forcedDist) {
            this.forcedDist = forcedDist;
        }
    }

    private LaunchResult launch(LaunchMode launchTarget, List<Path> additionalClassPath) {
        ModLoader.clear();

        System.setProperty("fml.earlyWindowControl", "false");

        var classLoader = Thread.currentThread().getContextClassLoader();
        var startupArgs = new StartupArgs(
                installation.getGameDir(),
                installation.getGameDir().resolve(".cache"),
                true,
                launchTarget.forcedDist,
                true,
                new String[] {
                        "--fml.mcVersion", SimulatedInstallation.MC_VERSION,
                        "--fml.neoForgeVersion", SimulatedInstallation.NEOFORGE_VERSION,
                        "--fml.neoFormVersion", SimulatedInstallation.NEOFORM_VERSION
                },
                locatedPaths.stream().map(Path::toFile).collect(Collectors.toSet()),
                additionalClassPath.stream().map(Path::toFile).toList(),
                classLoader);

        ClassLoader launchClassLoader;
        try {
            var instrumentation = ByteBuddyAgent.install();
            loader = FMLLoader.create(instrumentation, startupArgs);
            launchClassLoader = Thread.currentThread().getContextClassLoader();
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }

        var loadingModList = FMLLoader.getLoadingModList();
        var loadedMods = loadingModList.getModFiles();

        // Wait for background scans of all mods to complete
        for (var modFile : loadingModList.getModFiles()) {
            modFile.getFile().getScanResult();
        }

        var discoveryResult = loader.discoveryResult;

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
                launchClassLoader);
    }

    private void loadMods() {
        ModLoader.gatherAndInitializeMods(
                Runnable::run,
                Runnable::run,
                () -> {});
    }

    protected List<String> getTranslatedIssues(LaunchResult launchResult) {
        return getTranslatedIssues(launchResult.issues());
    }

    protected List<String> getTranslatedIssues(List<ModLoadingIssue> issues) {
        return issues
                .stream()
                .map(issue -> issue.severity() + ": " + sanitizeIssueText(issue))
                .toList();
    }

    private String sanitizeIssueText(ModLoadingIssue issue) {
        var text = FMLTranslations.stripControlCodes(FMLTranslations.translateIssue(issue));

        // Dynamically generated classnames cannot be asserted against
        text = text.replaceAll("\\$MockitoMock\\$\\w+", "");

        // Normalize path separators so tests run on Linux and Windows
        text = text.replace("\\", "/");

        // Strip game dir prefix
        text = text.replace(installation.getGameDir().toString().replace("\\", "/") + "/", "");

        return text;
    }

    protected final <T> T withGameClassloader(Callable<T> r) throws Exception {
        var previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader.currentClassLoader());
            return r.call();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
