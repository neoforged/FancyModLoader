/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModLoadingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class InvalidModFileTests extends LauncherTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidModFiles")
    void testProductionClient(String ignored, List<IdentifiableContent> content, String expectedError) throws Exception {
        installation.setupProductionClient();
        installation.writeModJar("mod.jar", content.toArray(IdentifiableContent[]::new));

        // In production we expect these to be warnings
        expectedError = "WARNING: " + expectedError;

        var result = launchAndLoad("neoforgeclient");
        assertThat(getTranslatedIssues(result)).containsOnly(expectedError);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidModFiles")
    void testModDev(String ignored, List<IdentifiableContent> content, String expectedError) throws Exception {
        var classpath = installation.setupUserdevProject();
        var mainModule = installation.setupGradleModule(content.toArray(IdentifiableContent[]::new));
        classpath.addAll(mainModule);

        // In development we expect these to be fatal errors
        expectedError = "ERROR: " + expectedError;

        // In dev we have to substitute the source folder
        expectedError = expectedError.replace("mods/mod.jar", mainModule.getFirst().toString().replace('\\', '/'));

        // Tell FML that the classes and resources directory belong together
        SimulatedInstallation.setModFoldersProperty(Map.of("mod", mainModule));

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoadWithAdditionalClasspath("neoforgeclientdev", classpath));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(expectedError);
    }

    @Test
    void testInvalidJarFile() throws Exception {
        installation.setupProductionClient();

        var path = installation.getModsFolder().resolve("mod.jar");
        Files.write(path, new byte[] { 1, 2, 3 });

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        // Clear the cause, otherwise equality will fail
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: File mods/mod.jar is not a jar file");
    }

    @Test
    void testFileIsDirectoryInProduction() throws Exception {
        installation.setupProductionClient();

        var path = installation.getModsFolder().resolve("mod.jar");
        Files.createDirectories(path);

        var result = launchAndLoad("neoforgeclient");
        assertThat(getTranslatedIssues(result)).containsOnly("WARNING: File mods/mod.jar is not a valid mod file");
    }

    /**
     * Odd test, but FML itself should not crash if a folder on the classpath ends with .jar
     */
    @Test
    void testFileIsDirectoryInDevSucceeds() throws Exception {
        var classpath = installation.setupUserdevProject();

        // This essentially checks what happens if a class-path entry happens to end in .jar
        var folder = installation.getGameDir().resolve("broken.jar");
        Files.createDirectories(folder);
        classpath.add(folder);

        var result = launchAndLoadWithAdditionalClasspath("neoforgeclientdev", classpath);
        assertThat(result.issues()).isEmpty();
    }

    private static List<Arguments> invalidModFiles() {
        var result = new ArrayList<Arguments>();

        result.add(arguments(
                "Invalid FMLModType in MANIFEST.MF",
                List.of(new IdentifiableContent("INVALID_MANIFEST", "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFMLModType: XXX\n".getBytes())),
                "File mods/mod.jar has an unrecognized FML mod-type: 'XXX'"));

        result.add(arguments(
                "Old NeoForge or MinecraftForge mod",
                List.of(new IdentifiableContent("MOD_TOML", "META-INF/mods.toml")),
                "File mods/mod.jar is for Minecraft Forge or an older version of NeoForge, and cannot be loaded"));

        result.add(arguments(
                "Fabric mod",
                List.of(new IdentifiableContent("FABRIC_MOD_JSON", "fabric.mod.json")),
                "File mods/mod.jar is a Fabric mod and cannot be loaded"));

        result.add(arguments(
                "Ancient Forge mod",
                List.of(new IdentifiableContent("ANCIENT_FORGE", "mcmod.info")),
                "File mods/mod.jar is for an old version of Minecraft Forge and cannot be loaded"));

        result.add(arguments(
                "Quilt mod",
                List.of(new IdentifiableContent("QUILT", "quilt.mod.json")),
                "File mods/mod.jar is a Quilt mod and cannot be loaded"));

        result.add(arguments(
                "Liteloader mod",
                List.of(new IdentifiableContent("LITELOADER", "litemod.json")),
                "File mods/mod.jar is a LiteLoader mod and cannot be loaded"));

        result.add(arguments(
                "Optifine non-forge",
                List.of(new IdentifiableContent("OPTIFINE", "optifine/Installer.class")),
                "File mods/mod.jar is an incompatible version of OptiFine"));

        result.add(arguments(
                "Bukkit plugin",
                List.of(new IdentifiableContent("BUKKIT_PLUGIN", "plugin.yml")),
                "File mods/mod.jar is a Bukkit or Bukkit-implementor (Spigot, Paper, etc.) plugin and cannot be loaded"));

        return result;
    }
}
