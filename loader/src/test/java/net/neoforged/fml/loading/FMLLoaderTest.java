/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.ModWorkManager;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
        @Test
        void testProductionClientDiscovery() throws Exception {
            installation.setupProductionClient();

            var result = launchAndLoad("forgeclient");
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testProductionServerDiscovery() throws Exception {
            installation.setupProductionServer();

            var result = launchAndLoad("forgeserver");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftServerJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevServerDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("forgeserverdev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevDataDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("forgedatadev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testNeoForgeDevClientDiscovery() throws Exception {
            var result = launchAndLoadInNeoForgeDevEnvironment("forgeclientdev");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testUserDevServerDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("forgeserveruserdev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testUserDevDataDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("forgedatauserdev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        @Test
        void testUserDevClientDiscovery() throws Exception {
            var classpath = installation.setupUserdevProject();

            var result = launchAndLoadWithAdditionalClasspath("forgeclientuserdev", classpath);
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }
    }

    @Nested
    class WithMods {
        @Test
        void testProductionClientDiscovery() throws Exception {
            installation.setupProductionClient();
            installation.setupModInModsFolder("testmod1", "1.0");
            installation.setupModInModsFolder("testmod1", "1.0");

            var result = launchAndLoad("forgeclient");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsOnlyKeys("minecraft", "neoforge", "testmod1");
            assertThat(result.gameLayerModules()).containsOnlyKeys("minecraft", "neoforge", "testmod1");
            assertThat(result.pluginLayerModules()).isEmpty();

            installation.assertMinecraftClientJar(result);
            installation.assertNeoForgeJar(result);
        }

        /**
         * A jar without a manifest and without a neoforge.mods.toml is not loaded, but leads
         * to a warning.
         */
        @Test
        void testPlainJarInModsFolderIsNotLoadedButWarnedAbout() throws Exception {
            installation.setupProductionClient();
            installation.setupPlainJarInModsFolder("plainmod.jar");

            var result = launchAndLoad("forgeclient");

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

            var result = launchAndLoad("forgeclient");
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

            var result = launchAndLoad("forgeclient");

            var loadedMod = result.loadedMods().get("testmod1");
            assertNotNull(loadedMod);
            assertEquals("12.0", loadedMod.versionString());
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

            var result = launchAndLoadWithAdditionalClasspath("forgeclientuserdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).doesNotContainKey("mod");
            assertThat(result.gameLayerModules()).containsKey("mod");
            installation.assertModContent(result, "mod", List.of(entrypointClass, modManifest));
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

            var result = launchAndLoadWithAdditionalClasspath("forgeclientuserdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).doesNotContainKey("mod");
            assertThat(result.gameLayerModules()).containsKey("mod");
            installation.assertModContent(result, "mod", List.of(entrypointClass, modManifest));
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

            var result = launchAndLoadWithAdditionalClasspath("forgeclientuserdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).containsKey("mod");
            assertThat(result.gameLayerModules()).doesNotContainKey("mod");
            assertThat(result.loadedMods()).doesNotContainKey("mod");
            installation.assertSecureJarContent(result.pluginLayerModules().get("mod"), List.of(entrypointClass, modManifest));
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

            var result = launchAndLoadWithAdditionalClasspath("forgeclientuserdev", additionalClasspath);
            assertThat(result.pluginLayerModules()).doesNotContainKey("mod");
            assertThat(result.gameLayerModules()).doesNotContainKey("mod");
            assertThat(result.loadedMods()).doesNotContainKey("mod");
        }
    }

    @Nested
    class Errors {
        @ParameterizedTest
        @CsvSource(textBlock = """
                unknownloader|[1.0]|ERROR: Mod File testmod.jar needs language provider unknownloader:1.0 to load\\nWe have found -
                javafml|[1.0]|ERROR: Mod File testmod.jar needs language provider javafml:1.0 to load\\nWe have found 3.0.9999
                javafml|[999.0]|ERROR: Mod File testmod.jar needs language provider javafml:999.0 to load\\nWe have found 3.0.9999
                """, delimiter = '|')
        void testIncompatibleLoaderVersions(String requestedLoader, String requestedVersionRange, String expectedError) throws Exception {
            expectedError = expectedError.replace("\\n", "\n");

            installation.setupProductionClient();
            installation.buildModJar("testmod.jar")
                    .withModsToml(builder -> builder
                            .unlicensedJavaMod()
                            .setLoader(requestedLoader, requestedVersionRange)
                            .addMod("testmod"))
                    .build();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(expectedError);
        }

        @Test
        void testCorruptedServerInstallation() throws Exception {
            installation.setupProductionServer();

            var serverPath = installation.getLibrariesDir().resolve("net/minecraft/server/1.20.4-202401020304/server-1.20.4-202401020304-srg.jar");
            Files.delete(serverPath);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeserver"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Your NeoForge installation is corrupted, please try to reinstall");
        }

        @Test
        void testCorruptedClientInstallation() throws Exception {
            installation.setupProductionClient();

            var clientPath = installation.getLibrariesDir().resolve("net/minecraft/client/1.20.4-202401020304/client-1.20.4-202401020304-srg.jar");
            Files.delete(clientPath);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Your NeoForge installation is corrupted, please try to reinstall");
        }

        @Test
        void testInvalidJarFile() throws Exception {
            installation.setupProductionClient();

            var path = installation.getModsFolder().resolve("mod.jar");
            Files.write(path, new byte[] { 1, 2, 3 });

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            // Clear the cause, otherwise equality will fail
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: File mods/mod.jar is not a jar file");
        }

        /**
         * Tests that an unknown FMLModType is recorded as an error for that file.
         */
        @Test
        void testJarFileWithInvalidFmlModType() throws Exception {
            installation.setupProductionClient();

            var path = installation.writeModJar("test.jar", new IdentifiableContent("INVALID_MANIFEST", "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFMLModType: XXX\n".getBytes()));

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            // Clear the cause, otherwise equality will fail
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: File mods/test.jar is not a valid mod file");
        }

        /**
         * Test that a locator or reader returning a custom subclass of IModFile is reported.
         */
        @Test
        void testInvalidSubclassOfModFile() throws Exception {
            installation.setupProductionClient();

            installation.writeModJar("test.jar", CustomSubclassModFileReader.TRIGGER);

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: A technical error occurred during mod loading: Unexpected IModFile subclass: class net.neoforged.neoforgespi.locating.IModFile");
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

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly("ERROR: Mod testproject requires neoforge 999.6 or above\nCurrently, neoforge is 1\n");
        }

        @Test
        void testDuplicateMods() throws Exception {
            installation.setupProductionClient();

            installation.writeModJar("test1.jar", SimulatedInstallation.createMultiModsToml("mod_a", "1.0", "mod_c", "1.0"));
            installation.writeModJar("test2.jar", SimulatedInstallation.createMultiModsToml("mod_b", "1.0", "mod_c", "1.0"));

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
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

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
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

            var launchResult = launchAndLoad("forgeclient");
            assertThat(launchResult.loadedMods()).containsKey("testmod");

            var e = assertThrows(ModLoadingException.class, () -> ModLoader.dispatchParallelEvent("test", ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), () -> {}, FMLClientSetupEvent::new));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: testmod (testmod) encountered an error while dispatching the net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event\n"
                            + "java.lang.IllegalStateException: Exception Message");
        }

        @Test
        void testExceptionInInitTaskIsCollectedAsModLoadingIssue() throws Exception {
            installation.setupProductionClient();

            launchAndLoad("forgeclient");
            var e = assertThrows(ModLoadingException.class, () -> ModLoader.runInitTask("test", ModWorkManager.syncExecutor(), () -> {}, () -> {
                throw new IllegalStateException("Exception Message");
            }));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: An uncaught parallel processing error has occurred."
                            + "\njava.lang.IllegalStateException: Exception Message");
        }
    }

    @Nested
    class Warnings {
        @Test
        void testIncompatibleModsToml() throws Exception {
            installation.setupProductionClient();
            var path = installation.writeModJar("mod.jar", new IdentifiableContent("MOD_TOML", "META-INF/mods.toml"));

            var result = launchAndLoad("forgeclient");
            assertThat(result.issues()).containsOnly(
                    ModLoadingIssue.warning("fml.modloading.brokenfile.minecraft_forge", path).withAffectedPath(path));
        }

        @Test
        void testFabricMod() throws Exception {
            installation.setupProductionClient();
            var path = installation.writeModJar("mod.jar", new IdentifiableContent("FABRIC_MOD_JSON", "fabric.mod.json"));

            var result = launchAndLoad("forgeclient");
            assertThat(result.issues()).containsOnly(
                    ModLoadingIssue.warning("fml.modloading.brokenfile.fabric", path).withAffectedPath(path));
        }

        @Test
        void testFileIsDirectory() throws Exception {
            installation.setupProductionClient();

            var path = installation.getModsFolder().resolve("mod.jar");
            Files.createDirectories(path);

            var result = launchAndLoad("forgeclient");
            assertThat(getTranslatedIssues(result)).containsOnly("WARNING: File mods/mod.jar is not a valid mod file");
        }
    }
}
