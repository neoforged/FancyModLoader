/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.electronwill.nightconfig.core.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ModFileInfoTest {
    @Mock
    ModFile modFile;
    @Mock
    Consumer<IModFileInfo> callback;
    Config config = Config.inMemory();
    List<Object> mods = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(modFile.getFileName()).thenReturn("testmod.jar");

        // Set up a minimal valid configuration
        config.set("license", "unlicensed");
        mods = new ArrayList<>();
        config.set("mods", mods);

        var mod = config.createSubConfig();
        mod.set("modId", "testmod");
        mods.add(mod);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            license|Missing license (testmod.jar)
            mods|Missing mods list (testmod.jar)
            """, delimiter = '|')
    void testMissingRequiredFields(String missingField, String expectedError) {
        config.remove(missingField);
        assertThatThrownBy(() -> new ModFileInfo(modFile, new NightConfigWrapper(config), callback))
                .hasMessage(expectedError);
    }

    @Test
    void testSettingLoaderVersionWithoutLoaderIsAnError() {
        config.set("loaderVersion", "1.0");
        assertThatThrownBy(() -> new ModFileInfo(modFile, new NightConfigWrapper(config), callback))
                .hasMessage("You cannot specify a loaderVersion without specifying a modLoader (testmod.jar)");
    }

    @Test
    void testMinimalModInfo() {
        var info = new ModFileInfo(modFile, new NightConfigWrapper(config), callback);
        assertThat(info.requiredLanguageLoaders()).containsOnly(
                new IModFileInfo.LanguageSpec(null, null));
        assertSame(modFile, info.getFile());
        assertThat(info.getMods()).hasSize(1);

        var mod = info.getMods().getFirst();
        assertEquals("testmod", mod.getModId());
    }
}
