/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cpw.mods.jarhandling.JarResource;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.TransformingClassLoader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.IBindingsProvider;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.startup.StartupArgs;
import net.neoforged.fml.testlib.IdentifiableContent;
import net.neoforged.fml.testlib.SimulatedInstallation;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
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

    protected TransformingClassLoader gameClassLoader;

    @BeforeAll
    static void ensureAddOpensForModularClassLoader() {
        // We abuse the ByteBuddy agent that Mockito also uses to open java.lang to ModularClassLoader
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
        if (FMLLoader.currentOrNull() != null) {
            throw new IllegalStateException("A previous test leaked an active FMLLoader. These tests will fail.");
        }

        // Clear in case other tests have set it and failed to reset it
        SimulatedInstallation.setModFoldersProperty(Map.of());

        FMLLoader.bindings = new IBindingsProvider() {
            private volatile IEventBus bus;

            @Override
            public IEventBus getGameBus() {
                if (bus == null) {
                    synchronized (this) {
                        if (bus == null) {
                            bus = BusBuilder.builder()
                                    .classChecker(eventType -> {
                                        if (IModBusEvent.class.isAssignableFrom(eventType)) {
                                            throw new IllegalArgumentException("IModBusEvent events are not allowed on the game bus!");
                                        }
                                    }).build();
                        }
                    }
                }
                return bus;
            }
        };

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

        if (LoadingModList.get() != null) {
            for (var modFile : LoadingModList.get().getModFiles()) {
                modFile.getFile().close();
            }
            for (var modFile : LoadingModList.get().getPlugins()) {
                ((ModFile) modFile.getFile()).close();
            }
            for (var modFile : LoadingModList.get().getGameLibraries()) {
                ((ModFile) modFile).close();
            }
        }

        installation.close();
        FMLLoader.bindings = null;
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
        LaunchResult result;
        try {
            result = launch(launchTarget, List.of());
        } catch (ModLoadingException e) {
            throw e;
        } catch (Exception e) {
            throw new LaunchException(e);
        }
        // loadMods is usually triggered from NeoForge
        loadMods();
        return result;
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

        public static LaunchMode fromLaunchTarget(String launchTarget) {
            return switch (launchTarget) {
                case "neoforgeclient" -> PROD_CLIENT;
                case "neoforgeserver" -> PROD_SERVER;
                case "neoforgeclientdev" -> DEV_CLIENT;
                case "neoforgeserverdev" -> DEV_SERVER;
                case "neoforgeclientdatadev" -> DEV_CLIENT_DATA;
                case "neoforgeserverdatadev" -> DEV_SERVER_DATA;
                default -> throw new IllegalArgumentException("Unsupported launch target: " + launchTarget);
            };
        }
    }

    private LaunchResult launch(String launchTarget, List<Path> additionalClassPath) {
        var launchMode = LaunchMode.fromLaunchTarget(launchTarget);
        ModLoader.clear();

        System.setProperty("fml.earlyWindowControl", "false");

        var classLoader = Thread.currentThread().getContextClassLoader();
        var startupArgs = new StartupArgs(
                installation.getGameDir(),
                installation.getGameDir().resolve(".cache"),
                true,
                launchMode.forcedDist,
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

        gameClassLoader = (TransformingClassLoader) launchClassLoader;

        loader.getClassTransformer().getTransformers();

        Map<String, SecureJar> gameLayerModules = new HashMap<>();
        for (var module : gameClassLoader.getConfiguration().modules()) {
            String moduleName = module.name();
            // Find matching mod file
            Stream.concat(
                    discoveryResult.gameContent().stream(),
                    discoveryResult.gameLibraryContent().stream())
                    .filter(mf -> mf.getId().equals(moduleName))
                    .findFirst()
                    .ifPresent(mf -> gameLayerModules.put(mf.getId(), mf.getSecureJar()));
        }

        return new LaunchResult(
                discoveryResult.pluginContent().stream().collect(
                        Collectors.toMap(
                                ModFile::getId,
                                ModFile::getSecureJar)),
                gameLayerModules,
                loadingModList.getModLoadingIssues(),
                loadedMods.stream().collect(Collectors.toMap(
                        o -> o.getMods().getFirst().getModId(),
                        o -> o)),
                loader.getClassTransformer().getTransformers(),
                launchClassLoader);
    }

    private void loadMods() {
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

    protected final <T> T withGameClassloader(Callable<T> r) throws Exception {
        var previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader.currentClassLoader());
            return r.call();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
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

    public void assertMinecraftServerJar(LaunchResult launchResult) throws IOException {
        var expectedContent = new ArrayList<IdentifiableContent>();
        Collections.addAll(expectedContent, SimulatedInstallation.SERVER_EXTRA_JAR_CONTENT);
        expectedContent.add(SimulatedInstallation.PATCHED_SHARED);
        expectedContent.add(SimulatedInstallation.MINECRAFT_MODS_TOML);

        assertModContent(launchResult, "minecraft", expectedContent);
    }

    /**
     * Asserts a Minecraft Jar in the legacy installation mode where the Minecraft jar is assembled in-memory from different individual pieces.
     * The only noticeable difference is that the Minecraft jar does not have a neoforge.mods.toml.
     */
    public void assertLegacyMinecraftServerJar(LaunchResult launchResult) throws IOException {
        var expectedContent = new ArrayList<IdentifiableContent>();
        Collections.addAll(expectedContent, SimulatedInstallation.SERVER_EXTRA_JAR_CONTENT);
        expectedContent.add(SimulatedInstallation.PATCHED_SHARED);

        assertModContent(launchResult, "minecraft", expectedContent);
    }

    /**
     * Asserts a Minecraft Jar in the legacy installation mode where the Minecraft jar is assembled in-memory from different individual pieces.
     * The only noticeable difference is that the Minecraft jar does not have a neoforge.mods.toml.
     */
    public void assertLegacyMinecraftClientJar(LaunchResult launchResult, boolean production) throws IOException {
        var expectedContent = new ArrayList<IdentifiableContent>();
        if (production) {
            expectedContent.add(SimulatedInstallation.SHARED_ASSETS);
            expectedContent.add(SimulatedInstallation.CLIENT_ASSETS);
            expectedContent.add(SimulatedInstallation.MINECRAFT_VERSION_JSON);
        } else {
            Collections.addAll(expectedContent, SimulatedInstallation.CLIENT_EXTRA_JAR_CONTENT);
        }
        expectedContent.add(SimulatedInstallation.PATCHED_CLIENT);
        expectedContent.add(SimulatedInstallation.PATCHED_SHARED);

        assertModContent(launchResult, "minecraft", expectedContent);
    }

    public void assertMinecraftClientJar(LaunchResult launchResult, boolean production) throws IOException {
        var expectedContent = new ArrayList<IdentifiableContent>();
        expectedContent.add(SimulatedInstallation.MINECRAFT_VERSION_JSON);
        expectedContent.add(SimulatedInstallation.SHARED_ASSETS);
        expectedContent.add(SimulatedInstallation.CLIENT_ASSETS);
        expectedContent.add(SimulatedInstallation.MINECRAFT_MODS_TOML);
        expectedContent.add(SimulatedInstallation.PATCHED_CLIENT);
        expectedContent.add(SimulatedInstallation.PATCHED_SHARED);
        // In joined distributions, there's supposed to be a manifest
        if (!production) {
            expectedContent.add(SimulatedInstallation.RESOURCES_MANIFEST);
        }

        assertModContent(launchResult, "minecraft", expectedContent);
    }

    public void assertNeoForgeJar(LaunchResult launchResult) throws IOException {
        var expectedContent = List.of(
                SimulatedInstallation.NEOFORGE_ASSETS,
                SimulatedInstallation.NEOFORGE_CLASSES,
                SimulatedInstallation.NEOFORGE_MODS_TOML,
                SimulatedInstallation.NEOFORGE_MANIFEST);

        assertModContent(launchResult, "neoforge", expectedContent);
    }

    public void assertModContent(LaunchResult launchResult, String modId, Collection<IdentifiableContent> content) throws IOException {
        assertThat(launchResult.loadedMods()).containsKey(modId);

        var modFileInfo = launchResult.loadedMods().get(modId);
        assertNotNull(modFileInfo, "mod " + modId + " is missing");

        assertSecureJarContent(modFileInfo.getFile().getSecureJar(), content);
    }

    public void assertSecureJarContent(SecureJar jar, Collection<IdentifiableContent> content) throws IOException {
        var paths = listFilesRecursively(jar);

        assertThat(paths.keySet()).containsOnly(content.stream().map(IdentifiableContent::relativePath).toArray(String[]::new));

        for (var identifiableContent : content) {
            var expectedContent = identifiableContent.content();
            var actualContent = paths.get(identifiableContent.relativePath()).readAllBytes();
            if (isPrintableAscii(expectedContent) && isPrintableAscii(actualContent)) {
                assertThat(new String(actualContent)).isEqualTo(new String(expectedContent));
            } else {
                assertThat(actualContent).isEqualTo(expectedContent);
            }
        }
    }

    private boolean isPrintableAscii(byte[] potentialText) {
        for (byte b : potentialText) {
            if (b < 0x20 || b == 0x7f) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, JarResource> listFilesRecursively(SecureJar jar) {
        Map<String, JarResource> paths = new HashMap<>();
        jar.contents().visitContent((relativePath, resource) -> {
            paths.put(relativePath, resource.retain());
        });
        return paths;
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
