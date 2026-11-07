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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
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
    private static final String NESTED_JAR_ENTRY = "META-INF/jarjar/nested-1.0.jar";

    private byte[] buildModWithNestedMod() throws Exception {
        var outerJar = installation.buildModJar("outer.jar")
                .withMod("outer", "1.0")
                .withJarInJar(new ContainedJarIdentifier("modgroup", "nested"), child -> child.withMod("nested", "1.0"))
                .build();
        try (var zip = new ZipFile(outerJar.toFile())) {
            return zip.getInputStream(zip.getEntry(NESTED_JAR_ENTRY)).readAllBytes();
        }
    }

    private Path jijCachePath(byte[] nestedBytes) throws Exception {
        var checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nestedBytes));
        return installation.getGameDir().resolve(".cache/jij").resolve(checksum).resolve("nested-1.0.jar").toAbsolutePath().normalize();
    }

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
        @SuppressWarnings("unchecked")
        void testJarInJarExtractsToDiskAndClosesUnusedJars() throws Exception {
            installation.setupProductionClient();
            installation.setupModInModsFolder("superseded_mod", "2.0");

            var range1To2 = new ContainedVersion(VersionRange.createFromVersionSpec("[1.0,2.0)"), new DefaultArtifactVersion("1.0"));
            var range1To2V15 = new ContainedVersion(VersionRange.createFromVersionSpec("[1.0,2.0)"), new DefaultArtifactVersion("1.5"));
            var range1To3 = new ContainedVersion(VersionRange.createFromVersionSpec("[1.0,3.0)"), new DefaultArtifactVersion("1.0"));

            installation.buildModJar("outer1.jar")
                    .withMod("outer1", "1.0")
                    .withJarInJar(new ContainedJarIdentifier("modgroup", "shared_dep"), range1To2, child -> child.withMod("shared_dep", "1.0"))
                    .withJarInJar(new ContainedJarIdentifier("modgroup", "superseded_mod"), range1To3, child -> child.withMod("superseded_mod", "1.0"))
                    .build();

            installation.buildModJar("outer2.jar")
                    .withMod("outer2", "1.0")
                    .withJarInJar(new ContainedJarIdentifier("modgroup", "shared_dep"), range1To2V15, child -> child.withMod("shared_dep", "1.5"))
                    .build();

            var unionProvider = FileSystemProvider.installedProviders().stream()
                    .filter(p -> p.getScheme().equals("union"))
                    .findFirst()
                    .orElseThrow();
            var unionFsField = unionProvider.getClass().getDeclaredField("fileSystems");
            unionFsField.setAccessible(true);
            var unionFsMap = (Map<String, FileSystem>) unionFsField.get(unionProvider);
            Set<String> baselineUnionKeys = new HashSet<>(unionFsMap.keySet());

            var jijProvider = FileSystemProvider.installedProviders().stream()
                    .filter(p -> p.getScheme().equals("jij"))
                    .findFirst()
                    .orElseThrow();
            var jijFsField = jijProvider.getClass().getSuperclass().getDeclaredField("fileSystems");
            jijFsField.setAccessible(true);
            var jijFsMap = (Map<String, FileSystem>) jijFsField.get(jijProvider);

            var result = launchAndLoad("forgeclient");
            assertThat(result.issues()).isEmpty();
            assertEquals("1.5", result.loadedMods().get("shared_dep").versionString());
            assertEquals("2.0", result.loadedMods().get("superseded_mod").versionString());

            var primaryPath = result.loadedMods().get("shared_dep").getFile().getFilePath();
            assertEquals("file", primaryPath.toUri().getScheme());
            assertThat(primaryPath).startsWith(FMLPaths.JIJ_CACHEDIR.get());
            assertThat(jijFsMap).isEmpty();

            Set<String> newUnionKeys = new HashSet<>(unionFsMap.keySet());
            newUnionKeys.removeAll(baselineUnionKeys);
            assertThat(newUnionKeys).anyMatch(key -> key.contains("/.cache/jij/") && key.contains("/shared_dep-1.5.jar"));
            assertThat(newUnionKeys).anyMatch(key -> key.contains("/mods/superseded_mod-2.0.jar"));
            // shared_dep-1.0 lost version selection and the nested superseded_mod-1.0 lost to the copy in mods/, so both must be closed
            assertThat(newUnionKeys)
                    .filteredOn(key -> key.contains("/.cache/jij/"))
                    .allMatch(key -> key.contains("/shared_dep-1.5.jar"));
        }

        @Test
        void testExistingJarInJarCacheEntryIsReused() throws Exception {
            installation.setupProductionClient();
            var nestedBytes = buildModWithNestedMod();
            var cachedPath = jijCachePath(nestedBytes);
            Files.createDirectories(cachedPath.getParent());
            Files.write(cachedPath, nestedBytes);
            var lastModified = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
            Files.setLastModifiedTime(cachedPath, lastModified);

            var result = launchAndLoad("forgeclient");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsKey("nested");
            assertEquals(cachedPath, result.loadedMods().get("nested").getFile().getFilePath());
            assertEquals(lastModified, Files.getLastModifiedTime(cachedPath));
        }

        @Test
        void testTruncatedJarInJarCacheEntryIsReplaced() throws Exception {
            installation.setupProductionClient();
            var nestedBytes = buildModWithNestedMod();
            var cachedPath = jijCachePath(nestedBytes);
            Files.createDirectories(cachedPath.getParent());
            Files.write(cachedPath, Arrays.copyOf(nestedBytes, nestedBytes.length / 2));

            var result = launchAndLoad("forgeclient");
            assertThat(result.issues()).isEmpty();
            assertThat(result.loadedMods()).containsKey("nested");
            assertThat(Files.readAllBytes(cachedPath)).isEqualTo(nestedBytes);
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
                unknownloader|[1.0]|ERROR: Mod File mods/testmod.jar needs language provider unknownloader:1.0 to load\\nWe have found -
                javafml|[1.0]|ERROR: Mod File mods/testmod.jar needs language provider javafml:1.0 to load\\nWe have found 3.0.9999
                javafml|[999.0]|ERROR: Mod File mods/testmod.jar needs language provider javafml:999.0 to load\\nWe have found 3.0.9999
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
        void testJarInJarExtractionFailureIsReportedAsIssue() throws Exception {
            installation.setupProductionClient();
            var nestedBytes = buildModWithNestedMod();
            // We put a regular file where the jar's checksum directory belongs so that creating the directory fails
            var checksumDir = jijCachePath(nestedBytes).getParent();
            Files.createDirectories(checksumDir.getParent());
            Files.writeString(checksumDir, "not a directory");

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues()))
                    .anyMatch(issue -> issue.startsWith("ERROR: A technical error occurred during mod loading: Failed to extract META-INF/jarjar/nested-1.0.jar"));
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
        void testDependencyCycle() throws Exception {
            installation.setupProductionClient();
            installation.buildModJar("mod_one.jar")
                    .withModsToml(builder -> {
                        builder.unlicensedJavaMod();
                        builder.addMod("mod_one", "1.0");
                        builder.customize(config -> {
                            var dependency = Config.inMemory();
                            dependency.set("modId", "mod_two");
                            dependency.set("versionRange", "[1.0,)");
                            dependency.set("ordering", "BEFORE");
                            config.set(List.of("dependencies", "mod_one"), List.of(dependency));
                        });
                    })
                    .build();
            installation.buildModJar("mod_two.jar")
                    .withModsToml(builder -> {
                        builder.unlicensedJavaMod();
                        builder.addMod("mod_two", "1.0");
                        builder.customize(config -> {
                            var dependency = Config.inMemory();
                            dependency.set("modId", "mod_three");
                            dependency.set("versionRange", "[1.0,)");
                            dependency.set("ordering", "BEFORE");
                            config.set(List.of("dependencies", "mod_two"), List.of(dependency));
                        });
                    })
                    .build();
            installation.buildModJar("mod_three.jar")
                    .withModsToml(builder -> {
                        builder.unlicensedJavaMod();
                        builder.addMod("mod_three", "1.0");
                        builder.customize(config -> {
                            var dependency = Config.inMemory();
                            dependency.set("modId", "mod_one");
                            dependency.set("versionRange", "[1.0,)");
                            dependency.set("ordering", "BEFORE");
                            config.set(List.of("dependencies", "mod_three"), List.of(dependency));
                        });
                    })
                    .build();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly("ERROR: Detected a mod dependency cycle: mod_one, mod_three, mod_two");
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
            assertThat(launchAndLoad("forgeclient").issues()).isEmpty();
        }

        @Test
        void testInvalidDependencyOverride() throws Exception {
            installation.setupProductionClient();

            // Test that invalid targets and dependencies warn
            installation.writeConfig("[dependencyOverrides]", "unknownmod = [\"-testmod\"]", "testmod = [\"+depdoesntexist\"]");
            installation.buildModJar("testmod.jar").withMod("testmod", "1.0").build();

            var r = launchAndLoad("forgeclient");
            assertThat(getTranslatedIssues(r.issues())).containsOnly(
                    "WARNING: Unknown dependency override target with id unknownmod",
                    "WARNING: Unknown mod depdoesntexist referenced in dependency overrides for mod testmod");
        }

        @Test
        void testIncompatibleMod() throws Exception {
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
                    modId="badmod"
                    type="incompatible"
                    reason="badmod patches testproject internals and breaks compatibility"
                    versionRange="[1,)"
                    ordering="NONE"
                    side="BOTH"
                    """.getBytes()));
            installation.buildModJar("badmod.jar").withMod("badmod", "1.0").build();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Mod testproject is incompatible with badmod 1 or above"
                            + "\nCurrently, badmod is 1.0"
                            + "\nThe reason is: badmod patches testproject internals and breaks compatibility");
        }

        @Test
        void testIncompatibleModWithoutReason() throws Exception {
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
                    modId="badmod"
                    type="incompatible"
                    versionRange="[1,)"
                    ordering="NONE"
                    side="BOTH"
                    """.getBytes()));
            installation.buildModJar("badmod.jar").withMod("badmod", "1.0").build();

            var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
            assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                    "ERROR: Mod testproject is incompatible with badmod 1 or above"
                            + "\nCurrently, badmod is 1.0"
                            + "\nThe reason is: No reason provided");
        }

        @Test
        void testDiscouragedMod() throws Exception {
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
                    modId="badmod"
                    type="discouraged"
                    reason="badmod patches testproject internals and may cause issues"
                    versionRange="[1,)"
                    ordering="NONE"
                    side="BOTH"
                    """.getBytes()));
            installation.buildModJar("badmod.jar").withMod("badmod", "1.0").build();

            var result = launchAndLoad("forgeclient");
            assertThat(getTranslatedIssues(result)).containsOnly(
                    "WARNING: Mod testproject discourages the use of badmod 1 or above"
                            + "\nCurrently, badmod is 1.0"
                            + "\nThe reason is: badmod patches testproject internals and may cause issues",
                    "WARNING: Proceed at your own risk");
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
}
