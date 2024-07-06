/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;


import cpw.mods.modlauncher.TransformingClassLoader;
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
import net.bytebuddy.agent.ByteBuddyAgent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fmlstartup.api.StartupArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.junit.jupiter.MockitoSettings;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;
import org.spongepowered.asm.service.modlauncher.MixinServiceModLauncher;

@MockitoSettings
public abstract class LauncherTest {
    protected SimulatedInstallation installation;

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
        // Clear in case other tests have set it and failed to reset it
        SimulatedInstallation.setModFoldersProperty(Map.of());
        installation = new SimulatedInstallation();
    }

    @AfterEach
    void clearSystemProperties() throws Exception {
        gameClassLoader = null;
        installation.close();
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
            var result = launch(launchTarget, additionalClassPath);
            // while loadMods is usually triggered from NeoForge
            loadMods();
            return result;
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    protected LaunchResult launchAndLoad(String launchTarget) {
        // launch represents the modlauncher portion
        LaunchResult result = launch(launchTarget);
        // while loadMods is usually triggered from NeoForge
        loadMods();
        return result;
    }

    private LaunchResult launch(String launchTarget) {
        return launch(launchTarget, List.of());
    }

    private LaunchResult launch(String launchTarget, List<Path> additionalClassPath) {
        resetMixin();

        ModLoader.clearLoadingIssues();
        var lml = FMLLoader.getLoadingModList();
        if (lml != null) {
            lml.getModLoadingIssues().clear();
        }

        System.setProperty("fml.earlyWindowControl", "false");

        var classLoader = Thread.currentThread().getContextClassLoader();
        var startupArgs = new StartupArgs(
                installation.getGameDir().toFile(),
                launchTarget,
                new String[] {
                        "--fml.fmlVersion", SimulatedInstallation.FML_VERSION,
                        "--fml.mcVersion", SimulatedInstallation.MC_VERSION,
                        "--fml.neoForgeVersion", SimulatedInstallation.NEOFORGE_VERSION,
                        "--fml.neoFormVersion", SimulatedInstallation.NEOFORM_VERSION
                },
                locatedPaths.stream().map(Path::toFile).collect(Collectors.toSet()),
                additionalClassPath.stream().map(Path::toFile).toList(),
                true,
                classLoader);

        ClassLoader launchClassLoader;
        try {
            var instrumentation = ByteBuddyAgent.install();
            FMLLoader.startup(instrumentation, startupArgs);
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
                launchClassLoader);
    }

    private static void resetMixin() {
        // There is sadly no way to "reset" Mixin once it's loaded. So, we use this hack.
        try {
            var serviceInstance = ReflectionUtils.tryToReadFieldValue(MixinService.class, "instance", null);
            if (serviceInstance.get() != null) {
                var f = MixinServiceModLauncher.class.getDeclaredField("initialised");
                f.setAccessible(true);
                try {
                    f.set(MixinService.getService(), false);
                } catch (ServiceNotAvailableError ignored) {}
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loadMods() {
        FMLLoader.progressWindowTick = () -> {};

        gameClassLoader = FMLLoader.gameClassLoader;

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
