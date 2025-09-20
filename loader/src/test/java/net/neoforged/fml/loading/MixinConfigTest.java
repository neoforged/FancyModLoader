/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.mixin.FMLMixinService;
import net.neoforged.fml.loading.mixin.MixinFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;

public class MixinConfigTest extends LauncherTest {
    @BeforeEach
    void cleanUpMixins() {
        // What a mess. If we never successfully initialized Mixin
        // we cannot even load the class, since it'd try to initialize a default service.
        // See MixinFacade for details
        if (System.getProperty("mixin.service") == null) {
            return;
        }

        try {
            Mixins.getConfigs().clear();
        } catch (ServiceNotAvailableError ignored) {}

        clearCollection(Config.class, null, "allConfigs");
        clearCollection(Mixins.class, null, "registeredConfigs");

        var service = (FMLMixinService) MixinService.getService();
        service.clearMixinContainers();

        // Clear root platform manager
        var platform = MixinBootstrap.getPlatform();
        clearCollection(MixinPlatformManager.class, platform, "containers");
        setField(MixinPlatformManager.class, platform, "injected", false);
        setField(MixinBootstrap.class, null, "platform", null); // Make MixinBootstrap call init on the platform again

        gotoPhase(MixinEnvironment.Phase.PREINIT);
    }

    private void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            var m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, phase);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> void setField(Class<?> clazz, T instance, String fieldName, Object value) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field value " + fieldName, e);
        }
    }

    private static <T> void clearCollection(Class<T> clazz, T instance, String fieldName) {
        Object obj;
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            obj = field.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to get field value " + fieldName, e);
        }
        switch (obj) {
            case Map<?, ?> map -> map.clear();
            case Collection<?> collection -> collection.clear();
            case null -> {}
            default -> throw new IllegalStateException("Don't know how to clear " + obj);
        }
    }

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
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json", "0.14.0"))
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
