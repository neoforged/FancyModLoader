/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.electronwill.nightconfig.core.Config;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;
import net.neoforged.fml.FMLVersion;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModWorkManager;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLanguageProvider;
import net.neoforged.fml.testlib.IdentifiableContent;
import net.neoforged.fml.testlib.SimulatedInstallation;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FMLLoaderTest extends LauncherTest {
    private static final ContainedVersion JIJ_V1 = new ContainedVersion(VersionRange.createFromVersion("1.0"), new DefaultArtifactVersion("1.0"));

    @Nested
    class WithoutMods {
        /**
         * Tests the new approach where minecraft and neoforge jar are on-disk in the way they
         * should be loaded.
         */
        @Test
        void testProductionClientDiscovery() throws Exception {
            installation.setupProductionClient();

            var result = launchAndLoad("neoforgeclient");
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertMinecraftClientJar(result, true);
            assertNeoForgeJar(result);
        }

        @Test
        void testProductionClientDiscoveryLegacyApproach() throws Exception {
            installation.setupProductionClientLegacy();

            var result = launchAndLoad("neoforgeclient");
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, true);
            assertNeoForgeJar(result);
        }

        @Test
        void testProductionServerDiscovery() throws Exception {
            installation.setupProductionServer();

            var result = launchAndLoad("neoforgeserver");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertMinecraftServerJar(result);
            assertNeoForgeJar(result);
        }

        @Test
        void testProductionServerDiscoveryLegacyApproach() throws Exception {
            installation.setupProductionServerLegacy();

            var result = launchAndLoad("neoforgeserver");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftServerJar(result);
            assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevServerDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("neoforgeserverdev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevClientDataDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("neoforgeclientdatadev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevJarClientDiscovery() throws Exception {
            var additionalClasspath = installation.setupSplitNeoForgeDevProjectForClientLaunch();
            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdatadev", additionalClasspath);

            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevServerDataDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("neoforgeserverdatadev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevClientDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("neoforgeclientdev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testUserDevServerDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("neoforgeserverdev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testUserDevClientSeparateJarsDiscovery() throws Exception {
            var classpath = installation.setupUserdevProjectNew();

            var result = launchAndLoadWithAdditionalClasspath("neoforgeserverdev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testUserServerDevDataDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("neoforgeserverdatadev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testUserClientDevDataDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdatadev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }

        @Test
        void testUserDevClientDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertLegacyMinecraftClientJar(result, false);
            assertNeoForgeJar(result);
        }
    }

    @Nested
    class WithMods {
        @Test
        void testProductionClientDiscovery() throws Exception {
            installation.setupProductionClient();
            installation.setupModInModsFolder("testmod1", "1.0");
            installation.setupModInModsFolder("testmod1", "1.0");

            var result = launchAndLoad("neoforgeclient");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge", "testmod1");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge", "testmod1");
            assertThat(result.pluginLayerModules()).isEmpty();

            assertMinecraftClientJar(result, true);
            assertNeoForgeJar(result);
        }

        /**
         * A jar without a manifest and without a neoforge.mods.toml is not loaded, but leads
         * to a warning.
         */
        @Test
        void testPlainJarInModsFolderIsNotLoadedButWarnedAbout() throws Exception {
            installation.setupProductionClient();
            installation.setupPlainJarInModsFolder("plainmod.jar");

            var result = launchAndLoad("neoforgeclient");

            assertThat(result.gameLayerModules()).doesNotContainKey("plainmod");
            assertThat(result.pluginLayerModules()).doesNotContainKey("plainmod");
            assertThat(getTranslatedIssues(result)).containsOnly(
                    "WARNING: File mods/plainmod.jar is not a valid mod file");
        }

        /**
         * A mod-jar that contains another mod and a plugin jar.
         */
        @Test
        void testJarInJar() throws Exception {
            installation.setupProductionClient();
            installation.writeModJar("jijmod.jar",
                    SimulatedInstallation.createModsToml("jijmod", "1.0"),
                    SimulatedInstallation.createJarFile(
                            "EMBEDDED_MOD", "META-INF/jarjar/embedded_mod-1.0.jar", SimulatedInstallation.createModsToml("embeddedmod", "1.0")),
                    SimulatedInstallation.createJarFile(
                            "EMBEDDED_SERVICE", "META-INF/jarjar/embedded_service-1.0.jar", SimulatedInstallation.createManifest("SERVICE_MANIFEST", Map.of("FMLModType", "LIBRARY"))),
                    SimulatedInstallation.createJarFile(
                            "EMBEDDED_GAMELIB", "META-INF/jarjar/embedded_gamelib-1.0.jar", SimulatedInstallation.createManifest("GAMELIB_MANIFEST", Map.of("FMLModType", "GAMELIBRARY"))),
                    SimulatedInstallation.createJarFile(
                            "EMBEDDED_LIB", "META-INF/jarjar/embedded_lib-1.0.jar"),
                    SimulatedInstallation.createJijMetadata(
                            new ContainedJarMetadata(new ContainedJarIdentifier("modgroup", "embedded-mod"), JIJ_V1, "META-INF/jarjar/embedded_mod-1.0.jar", false),
                            new ContainedJarMetadata(new ContainedJarIdentifier("modgroup", "embedded-service"), JIJ_V1, "META-INF/jarjar/embedded_service-1.0.jar", false),
                            new ContainedJarMetadata(new ContainedJarIdentifier("modgroup", "embedded-gamelib"), JIJ_V1, "META-INF/jarjar/embedded_gamelib-1.0.jar", false),
                            new ContainedJarMetadata(new ContainedJarIdentifier("modgroup", "embedded-lib"), JIJ_V1, "META-INF/jarjar/embedded_lib-1.0.jar", false)));

            var result = launchAndLoad("neoforgeclient");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "embeddedmod", "embedded.gamelib", "jijmod", "neoforge");
            assertThat(result.pluginLayerModules()).containsOnlyKeys("embedded.lib", "embedded.service");
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge", "embeddedmod", "jijmod");
            assertThat(result.issues()).isEmpty();
        }

        /**
         * If a mod file is present in multiple versions, the latest one is used.
         */
        @Test
        void testHighestVersionWins() throws Exception {
            installation.setupProductionClient();
            installation.setupModInModsFolder("testmod1", "1.0");
            installation.setupModInModsFolder("testmod1", "12.0");
            installation.setupModInModsFolder("testmod1", "3.0");

            var result = launchAndLoad("neoforgeclient");

            var loadedMod = result.loadedMods().get("testmod1");
            assertNotNull(loadedMod);
            assertEquals("12.0", loadedMod.versionString());
        }

        /**
         * If a game library is present in multiple versions, the latest one is used.
         */
        @Test
        void testHighestGameLibraryVersionWins() throws Exception {
            installation.setupProductionClient();
            installation.buildModJar("testlib-1.0.jar")
                    .addClass("testlib.TestClass", "class TestClass {}")
                    .withManifest(Map.of(
                            "Automatic-Module-Name", "testlib",
                            "FMLModType", "GAMELIBRARY",
                            "Implementation-Version", "1.0"))
                    .build();
            var pickedJarPath = installation.buildModJar("testlib-2.0.jar")
                    .addClass("testlib.TestClass", "class TestClass {}")
                    .withManifest(Map.of(
                            "Automatic-Module-Name", "testlib",
                            "FMLModType", "GAMELIBRARY",
                            "Implementation-Version", "2.0"))
                    .build();

            var result = launchAndLoad("neoforgeclient");
            assertThat(result.gameLayerModules()).containsKey("testlib");

            var loadedMod = result.gameLayerModules().get("testlib");
            assertNotNull(loadedMod);
            assertEquals(pickedJarPath, loadedMod.getPrimaryPath());
        }

        /**
         * If a library is present in multiple versions, the latest one is used.
         */
        @Test
        void testHighestLibraryVersionWins() throws Exception {
            installation.setupProductionClient();
            installation.buildModJar("testlib-1.0.jar")
                    .addClass("testlib.TestClass", "class TestClass {}")
                    .withManifest(Map.of(
                            "Automatic-Module-Name", "testlib",
                            "FMLModType", "LIBRARY",
                            "Implementation-Version", "1.0"))
                    .build();
            var pickedJarPath = installation.buildModJar("testlib-2.0.jar")
                    .addClass("testlib.TestClass", "class TestClass {}")
                    .withManifest(Map.of(
                            "Automatic-Module-Name", "testlib",
                            "FMLModType", "LIBRARY",
                            "Implementation-Version", "2.0"))
                    .build();

            var result = launchAndLoad("neoforgeclient");

            var loadedMod = result.pluginLayerModules().get("testlib");
            assertNotNull(loadedMod);
            assertEquals(pickedJarPath, loadedMod.getPrimaryPath());
        }

        /**
         * Tests that a lowcode mod is automatically redirected to the javafml loader.
         */
        @Test
        void testLowCodeIsRedirectToJava() throws Exception {
            installation.setupProductionClient();
            installation.buildModJar("testmod1.jar")
                    .withModsToml(modsToml -> {
                        modsToml.license("unlicensed");
                        modsToml.setLoader("lowcodefml", "[99999]");

                        modsToml.addMod("testmod1", "1.0");
                    })
                    .build();

            var result = launchAndLoad("neoforgeclient");

            var loadedMod = result.loadedMods().get("testmod1");
            assertNotNull(loadedMod);
            assertEquals("1.0", loadedMod.versionString());
            var modInfo = loadedMod.getMods().getFirst();
            assertThat(modInfo.getLoader()).isInstanceOf(FMLJavaModLanguageProvider.class);
        }

        @Test
        void testUserdevWithModProject() throws Exception {
            var additionalClasspath = installation.setupUserdevProject();

            var entrypointClass = SimulatedInstallation.generateClass("MOD_ENTRYPOINT", "mod/Entrypoint.class");
            var modManifest = SimulatedInstallation.createModsToml("mod", "1.0.0");

            var mainModule = installation.setupGradleModule(entrypointClass, modManifest);
            additionalClasspath.addAll(mainModule);

            // Tell FML that the classes and resources directory belong together
            SimulatedInstallation.setModFoldersProperty(Map.of("mod", mainModule));

            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).doesNotContainKey("mod");
            assertThat(result.gameLayerModules()).containsKey("mod");
            assertModContent(result, "mod", List.of(entrypointClass, modManifest));
        }

        /**
         * Special test-case that checks we can add additional candidates via the modFolders system property,
         * even if they are not on the classpath.
         */
        @Test
        void testUserdevWithModProjectNotOnClasspath() throws Exception {
            var additionalClasspath = installation.setupUserdevProject();

            var entrypointClass = SimulatedInstallation.generateClass("MOD_ENTRYPOINT", "mod/Entrypoint.class");
            var modManifest = SimulatedInstallation.createModsToml("mod", "1.0.0");

            var mainModule = installation.setupGradleModule(entrypointClass, modManifest);
            // NOTE: mainModule is not added to the classpath here

            // Tell FML that the classes and resources directory belong together
            SimulatedInstallation.setModFoldersProperty(Map.of("mod", mainModule));

            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).doesNotContainKey("mod");
            assertThat(result.gameLayerModules()).containsKey("mod");
            assertModContent(result, "mod", List.of(entrypointClass, modManifest));
        }

        @Test
        void testUserdevWithServiceProject() throws Exception {
            var additionalClasspath = installation.setupUserdevProject();

            var entrypointClass = SimulatedInstallation.generateClass("MOD_SERVICE", "mod/SomeService.class");
            var modManifest = SimulatedInstallation.createManifest("mod", Map.of("Automatic-Module-Name", "mod", "FMLModType", "LIBRARY"));

            var mainModule = installation.setupGradleModule(entrypointClass, modManifest);
            additionalClasspath.addAll(mainModule);

            // Tell FML that the classes and resources directory belong together
            SimulatedInstallation.setModFoldersProperty(Map.of("mod", mainModule));

            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).containsKey("mod");
            assertThat(result.gameLayerModules()).doesNotContainKey("mod");
            assertThat(result.loadedMods()).doesNotContainKey("mod");
            assertSecureJarContent(result.pluginLayerModules().get("mod"), List.of(entrypointClass, modManifest));
        }

        /**
         * Check that a ModLauncher service does not end up being loaded twice.
         */
        @Test
        void testUserdevWithModLauncherServiceProject() throws Exception {
            var additionalClasspath = installation.setupUserdevProject();

            var entrypointClass = SimulatedInstallation.generateClass("MOD_SERVICE", "mod/SomeService.class");
            var modManifest = SimulatedInstallation.createManifest("mod", Map.of("Automatic-Module-Name", "mod", "FMLModType", "LIBRARY"));

            var mainModule = installation.setupGradleModule(entrypointClass, modManifest);
            additionalClasspath.addAll(mainModule);

            // Tell FML that the classes and resources directory belong together, this would also be read
            // by the Classpath ML locator
            SimulatedInstallation.setModFoldersProperty(Map.of("mod", mainModule));
            locatedPaths.add(mainModule.getFirst()); // Mark the primary path as located by ML so it gets skipped by FML

            var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).doesNotContainKey("mod");
            assertThat(result.gameLayerModules()).doesNotContainKey("mod");
            assertThat(result.loadedMods()).doesNotContainKey("mod");
        }
    }

    @Nested
    class Errors {
        @ParameterizedTest
        @CsvSource(textBlock = """
                unknownloader|[1.0]|ERROR: Mod File mods/testmod.jar needs language provider unknownloader to load
                javafml|[1.0]|ERROR: Mod File mods/testmod.jar needs language provider javafml:1.0 to load\\nWe have found $VERSION
                javafml|[999.0]|ERROR: Mod File mods/testmod.jar needs language provider javafml:999.0 to load\\nWe have found $VERSION
                """, delimiter = '|')
        void testIncompatibleLoaderVersions(String requestedLoader, String requestedVersionRange, String expectedError) throws Exception {
            expectedError = expectedError.replace("\\n", "\n");
            expectedError = expectedError.replace("$VERSION", FMLVersion.getVersion());

            installation.setupProductionClient();
            installation.buildModJar("testmod.jar")
                    .withModsToml(builder -> builder
                            .unlicensedJavaMod()
                            .setLoader(requestedLoader, requestedVersionRange)
                            .addMod("testmod"))
                    .build();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(expectedError);
        }

        @Test
        void testMissingMinecraftJarInClientInstallation() throws Exception {
            installation.setupProductionClient();

            var clientPath = installation.getLibrariesDir().resolve("net/neoforged/minecraft-client-patched/20.4.9999/minecraft-client-patched-20.4.9999.jar");
            Files.delete(clientPath);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Your NeoForge installation is corrupted. Please try to reinstall NeoForge.");
        }

        @Test
        void testCorruptedMinecraftJarInClientInstallation() throws Exception {
            installation.setupProductionClient();

            var clientPath = installation.getLibrariesDir().resolve("net/neoforged/minecraft-client-patched/20.4.9999/minecraft-client-patched-20.4.9999.jar");
            // Replace the jar with an empty zip (no neoforge.mods.toml)
            new ZipOutputStream(Files.newOutputStream(clientPath)).close();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: The patched Minecraft jar is corrupted. Please try to reinstall NeoForge.");
        }

        @Test
        void testMissingNeoForgeJarInClientInstallation() throws Exception {
            installation.setupProductionClient();

            var neoforgePath = installation.getLibrariesDir().resolve("net/neoforged/neoforge/20.4.9999/neoforge-20.4.9999-universal.jar");
            Files.delete(neoforgePath);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: The NeoForge jar is missing. Please try to reinstall NeoForge.");
        }

        @Test
        void testCorruptedNeoForgeJarInClientInstallation() throws Exception {
            installation.setupProductionClient();

            var clientPath = installation.getLibrariesDir().resolve("net/neoforged/neoforge/20.4.9999/neoforge-20.4.9999-universal.jar");
            // Replace the jar with an empty zip (no neoforge.mods.toml)
            new ZipOutputStream(Files.newOutputStream(clientPath)).close();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: The NeoForge jar is corrupted. Please try to reinstall NeoForge.");
        }

        @Test
        void testMissingMinecraftJarInServerInstallation() throws Exception {
            installation.setupProductionServer();

            var serverPath = installation.getLibrariesDir().resolve("net/neoforged/minecraft-server-patched/20.4.9999/minecraft-server-patched-20.4.9999.jar");
            Files.delete(serverPath);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeserver"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Your NeoForge installation is corrupted. Please try to reinstall NeoForge.");
        }

        @Test
        void testCorruptedMinecraftJarInServerInstallation() throws Exception {
            installation.setupProductionServer();

            var serverPath = installation.getLibrariesDir().resolve("net/neoforged/minecraft-server-patched/20.4.9999/minecraft-server-patched-20.4.9999.jar");
            // Replace the jar with an empty zip (no neoforge.mods.toml)
            new ZipOutputStream(Files.newOutputStream(serverPath)).close();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeserver"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: The patched Minecraft jar is corrupted. Please try to reinstall NeoForge.");
        }

        @Test
        void testMissingNeoForgeJarInServerInstallation() throws Exception {
            installation.setupProductionServer();

            var neoforgePath = installation.getLibrariesDir().resolve("net/neoforged/neoforge/20.4.9999/neoforge-20.4.9999-universal.jar");
            Files.delete(neoforgePath);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeserver"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: The NeoForge jar is missing. Please try to reinstall NeoForge.");
        }

        @Test
        void testCorruptedNeoForgeJarInServerInstallation() throws Exception {
            installation.setupProductionServer();

            var neoforgePath = installation.getComponentRoots().neoforgeCommonClassesRoot();
            // Replace the jar with an empty zip (no neoforge.mods.toml)
            new ZipOutputStream(Files.newOutputStream(neoforgePath)).close();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeserver"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: The NeoForge jar is corrupted. Please try to reinstall NeoForge.");
        }

        /**
         * Test that a locator or reader returning a custom subclass of IModFile is reported.
         */
        @Test
        void testInvalidSubclassOfModFile() throws Exception {
            installation.setupProductionClient();

            installation.writeModJar("test.jar", CustomSubclassModFileReader.TRIGGER);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: A technical error occurred during mod loading: Unexpected IModFile subclass: class org.mockito.codegen.IModFile");
        }

        @Test
        void testUnsatisfiedNeoForgeRange() throws Exception {
            installation.setupProductionClient();

            installation.writeModJar("test.jar", new IdentifiableContent("MODS_TOML", "META-INF/neoforge.mods.toml", """
                    modLoader="javafml"
                    loaderVersion="[3,)"
                    license="CC0"
                    [[mods]]
                    modId="testproject"
                    version="0.0.0"
                    displayName="Test Project"
                    description='''A test project.'''
                    [[dependencies.testproject]]
                    modId="neoforge"
                    type="required"
                    versionRange="[999.6,)"
                    ordering="NONE"
                    side="BOTH"
                    """.getBytes()));

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly("ERROR: Mod testproject requires neoforge 999.6 or above\nCurrently, neoforge is 1\n");
        }

        @Test
        void testDependencyOverride() throws Exception {
            installation.setupProductionClient();
            installation.writeConfig("[dependencyOverrides]", "targetmod = [\"-depmod\", \"-incompatiblemod\"]");
            installation.buildModJar("depmod.jar").withMod("depmod", "1.0").build();
            installation.buildModJar("incompatiblemod.jar").withMod("incompatiblemod", "1.0").build();
            installation.buildModJar("targetmod.jar")
                    .withModsToml(builder -> {
                        builder.unlicensedJavaMod();
                        builder.addMod("targetmod", "1.0", c -> {
                            var sub = Config.inMemory();
                            sub.set("modId", "depmod");
                            sub.set("versionRange", "[2,)");
                            sub.set("type", "required");

                            var sub2 = Config.inMemory();
                            sub2.set("modId", "incompatiblemod");
                            sub2.set("versionRange", "[1,");
                            sub2.set("type", "incompatible");
                            c.set("dependencies.targetmod", new ArrayList<>(Arrays.asList(sub, sub2)));
                        });
                    })
                    .build();
            assertThat(launchAndLoad("neoforgeclient").issues()).isEmpty();
        }

        @Test
        void testInvalidDependencyOverride() throws Exception {
            installation.setupProductionClient();

            // Test that invalid targets and dependencies warn
            installation.writeConfig("[dependencyOverrides]", "unknownmod = [\"-testmod\"]", "testmod = [\"+depdoesntexist\"]");
            installation.buildModJar("testmod.jar").withMod("testmod", "1.0").build();

            var r = launchAndLoad("neoforgeclient");
            assertThat(getTranslatedIssues(r.issues())).containsOnly(
                    "WARNING: Unknown dependency override target with id unknownmod",
                    "WARNING: Unknown mod depdoesntexist referenced in dependency overrides for mod testmod");
        }

        @Test
        void testDuplicateMods() throws Exception {
            installation.setupProductionClient();

            installation.writeModJar("test1.jar", SimulatedInstallation.createMultiModsToml("mod_a", "1.0", "mod_c", "1.0"));
            installation.writeModJar("test2.jar", SimulatedInstallation.createMultiModsToml("mod_b", "1.0", "mod_c", "1.0"));

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Mod mod_c is present in multiple files: test2.jar, test1.jar");
        }

        @Test
        void testMissingOrUnsatisfiedForgeFeatures() throws Exception {
            installation.setupProductionClient();

            installation.buildModJar("test1.jar")
                    .withModsToml(builder -> builder.unlicensedJavaMod()
                            .addMod("testmod", "1.0")
                            .withRequiredFeatures("testmod", Map.of(
                                    "javaVersion", "[999]",
                                    "thisFeatureDoesNotExist", "*")))
                    .build();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: testmod (testmod) is missing a feature it requires to run"
                            + "\nIt requires javaVersion 999 but " + System.getProperty("java.version") + " is available",
                    "ERROR: testmod (testmod) is missing a feature it requires to run"
                            + "\nIt requires thisFeatureDoesNotExist=\"*\" but NONE is available");
        }

        @Test
        void testExceptionInParallelEventDispatchIsCollectedAsModLoadingIssue() throws Exception {
            installation.setupProductionClient();

            installation.buildModJar("testmod.jar")
                    .withTestmodModsToml()
                    .addClass("testmod.Thrower", """
                            @net.neoforged.fml.common.Mod("testmod")
                            public class Thrower {
                                public Thrower(net.neoforged.bus.api.IEventBus modEventBus) {
                                    modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, e -> {
                                        throw new IllegalStateException("Exception Message");
                                    });
                                }
                            }
                            """)
                    .build();

            var launchResult = launchAndLoad("neoforgeclient");
            assertThat(launchResult.loadedMods()).containsKey("testmod");

            var e = assertThrows(ModLoadingException.class, () -> ModLoader.dispatchParallelEvent("test", ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), () -> {}, FMLClientSetupEvent::new));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: testmod (testmod) encountered an error while dispatching the net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event\n"
                            + "java.lang.IllegalStateException: Exception Message");
        }

        @Test
        void testExceptionInInitTaskIsCollectedAsModLoadingIssue() throws Exception {
            installation.setupProductionClient();

            launchAndLoad("neoforgeclient");
            var e = assertThrows(ModLoadingException.class, () -> ModLoader.runInitTask("test", ModWorkManager.syncExecutor(), () -> {}, () -> {
                throw new IllegalStateException("Exception Message");
            }));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: An uncaught parallel processing error has occurred."
                            + "\njava.lang.IllegalStateException: Exception Message");
        }
    }
}
