package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.mixin.FMLMixinLaunchPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

public class MixinConfigTest extends LauncherTest {
    @BeforeEach
    void cleanUpMixins() throws Exception {
        Mixins.getConfigs().clear();

        Field field = Config.class.getDeclaredField("allConfigs");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();

        field = Mixins.class.getDeclaredField("registeredConfigs");
        field.setAccessible(true);
        ((Set<?>) field.get(null)).clear();
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
                        .replace("$MV", FMLMixinLaunchPlugin.LOWEST_MIXIN_VERSION.toString()));
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
                        .replace("$MV", FMLMixinLaunchPlugin.HIGHEST_MIXIN_VERSION.toString()));
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
}
