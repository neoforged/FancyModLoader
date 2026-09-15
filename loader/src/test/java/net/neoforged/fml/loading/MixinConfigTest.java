/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.mixin.MixinFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.Mixins;

public class MixinConfigTest extends LauncherTest implements MixinTestHelper {
    @Test
    void testRequiredModIsMissing() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json", null, List.of("missingmod")))
                .addTextFile("test.mixins.json", "{}")
                .build();

        launchAndLoad("neoforgeclient");

        assertThat(Mixins.getConfigs()).isEmpty();
    }

    @Test
    void testRequiredModIsPresent() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json", null, List.of("requiredmod")))
                .addTextFile("test.mixins.json", "{}")
                .build();
        installation.buildModJar("requiredmod.jar")
                .withMod("requiredmod", "1")
                .build();

        launchAndLoad("neoforgeclient");

        assertThat(Mixins.getConfigs()).extracting("name").containsOnly("test.mixins.json");
    }

    @Test
    void testMissingMixinConfig() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json"))
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: A mixin config named test.mixins.json was declared in mods/mixin-test.jar, but doesn't exist");
    }

    // This test is for Archloom and mixin configs coming from the common module (which Archloom puts on the classpath separately)
    @Test
    void testMixinConfigComesFromOtherModFile() throws Exception {
        installation.setupUserdevProjectNew();
        installation.buildInstallationAppropriateModProject(null, "main.jar", builder -> builder.withTestmodModsToml(modsToml -> modsToml.addMixinConfig("testcommon.mixins.json")));
        installation.buildModJar("mixin-common.jar")
                .withMod("generated", "1.0")
                .addTextFile("testcommon.mixins.json", "{}")
                .build();

        var result = launchClient();
        assertThat(result.issues()).isEmpty();
        assertThat(Mixins.getConfigs()).extracting("name").containsOnly("testcommon.mixins.json");
    }

    @Test
    void testRequestedMixinBehaviorIsTooOld() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json", "0"))
                .addTextFile("test.mixins.json", "{}")
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: Mixin config test.mixins.json from mods/mixin-test.jar requests Mixin behavior version 0, which is older than the lowest supported version $MV"
                        .replace("$MV", MixinFacade.LOWEST_MIXIN_VERSION.toString()));
    }

    @Test
    void testRequestedMixinBehaviorIsTooNew() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json", "9999999"))
                .addTextFile("test.mixins.json", "{}")
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: Mixin config test.mixins.json from mods/mixin-test.jar requests Mixin behavior version 9999999, which is newer than the highest supported version $MV. This may be fixable by updating NeoForge"
                        .replace("$MV", MixinFacade.HIGHEST_MIXIN_VERSION.toString()));
    }

    @Test
    void testDuplicateMixinConfigs() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test1.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json"))
                .addTextFile("test.mixins.json", "{}")
                .build();
        installation.buildModJar("mixin-test2.jar")
                .withModsToml(modsToml -> modsToml.unlicensedJavaMod().addMixinConfig("test.mixins.json").addMod("testmod2"))
                .addTextFile("test.mixins.json", "{}")
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: A mixin config named test.mixins.json is provided by both mods/mixin-test1.jar and mods/mixin-test2.jar");
    }

    @Test
    void testRequestedMixinBehaviorIsValid() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json", "0.17.3"))
                .addTextFile("test.mixins.json", "{}")
                .build();

        launchAndLoad("neoforgeclient");

        assertThat(Mixins.getConfigs()).extracting("name").containsOnly("test.mixins.json");
    }

    /**
     * Tests that Mixin configs declared only via the manifest are correctly picked up by Mixin.
     * <p>This is used by mixinextras, for example.
     */
    @ParameterizedTest
    @ValueSource(strings = { "LIBRARY", "GAMELIBRARY" })
    void testMixinConfigDeclaredInManifestIsLoaded(String modType) throws Exception {
        installation.setupProductionClient();
        String mixinConfigFilename = "test." + modType + ".mixins.json";
        installation.buildModJar("mixin-test.jar")
                .withManifest(Map.of(
                        "MixinConfigs", mixinConfigFilename,
                        "FMLModType", modType))
                .addTextFile(mixinConfigFilename, "{}")
                .build();

        launchAndLoad("neoforgeclient");

        assertThat(Mixins.getConfigs()).extracting("name").containsOnly(mixinConfigFilename);
    }
}
