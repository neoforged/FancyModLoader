/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

public class ConfigTrackerTest {
    @TempDir
    Path tempDir;

    ModContainer modContainer;
    ConfigTracker configTracker;

    @BeforeEach
    void setupGlobalConfig() {
        FMLPaths.loadAbsolutePaths(tempDir.resolve("gamedir"));
        FMLConfig.load();

        modContainer = Mockito.mock(ModContainer.class);
        Mockito.when(modContainer.getModId()).thenReturn("configtestmod");

        configTracker = new ConfigTracker();
    }

    @Test
    void testOpenNewConfig() {
        var modConfig = configTracker.registerConfig(ModConfig.Type.CLIENT, new SimpleConfigSpec(), modContainer, "testOpenNewConfig.toml");

        configTracker.openConfig(modConfig, tempDir, null);

        Assertions.assertThat(modConfig.getFullPath())
                .exists()
                .hasContent("""
                        #Test comment:
                        configEntry = 4
                        """);
    }

    @Test
    void testOpenExistingConfig() throws IOException {
        var spec = new SimpleConfigSpec();
        var modConfig = configTracker.registerConfig(ModConfig.Type.CLIENT, spec, modContainer, "testOpenExistingConfig.toml");

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                configEntry = 10
                """);

        configTracker.openConfig(modConfig, tempDir, null);

        Assertions.assertThat(spec.loadedValue)
                .isEqualTo(10);
        Assertions.assertThat(modConfig.getFullPath())
                .exists()
                .hasContent("""
                        configEntry = 10
                        """);
    }

    @Test
    void testOpenInvalidFile() throws IOException {
        var modConfig = configTracker.registerConfig(ModConfig.Type.CLIENT, new SimpleConfigSpec(), modContainer, "testOpenInvalidFile.toml");

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                invalidtomlcontents
                """);

        configTracker.openConfig(modConfig, tempDir, null);

        Assertions.assertThat(modConfig.getFullPath())
                .exists()
                .hasContent("""
                        #Test comment:
                        configEntry = 4
                        """);
    }

    @Test
    void testCorrectOnOpen() throws IOException {
        var modConfig = configTracker.registerConfig(ModConfig.Type.CLIENT, new SimpleConfigSpec(), modContainer, "testCorrectOnOpen.toml");

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                otherConfigEntry = 1
                """);

        configTracker.openConfig(modConfig, tempDir, null);

        Assertions.assertThat(modConfig.getFullPath())
                .exists()
                .hasContent("""
                        otherConfigEntry = 1
                        #Test comment:
                        configEntry = 4
                        """);
    }

    @Test
    void testReload() throws Exception {
        var spec = new SimpleConfigSpec();
        var modConfig = configTracker.registerConfig(ModConfig.Type.CLIENT, spec, modContainer, "testReload.toml");

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                configEntry = 10
                """);

        configTracker.openConfig(modConfig, tempDir, null);

        Assertions.assertThat(spec.loadedValue)
                .isEqualTo(10);

        // Wait for the file watcher to be registered by NightConfig.
        // TODO: Reconsider when NightConfig supports waiting for the watcher registration.
        Thread.sleep(250);

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                configEntry = 5
                """);

        waitUntil(() -> {
            Assertions.assertThat(spec.loadedValue)
                    .isEqualTo(5);
        });
    }

    @Test
    void testCorrectOnReload() throws Exception {
        var spec = new SimpleConfigSpec();
        var modConfig = configTracker.registerConfig(ModConfig.Type.CLIENT, spec, modContainer, "testCorrectOnReload.toml");

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                configEntry = 10
                """);

        configTracker.openConfig(modConfig, tempDir, null);

        Assertions.assertThat(spec.loadedValue)
                .isEqualTo(10);

        // Wait for the file watcher to be registered by NightConfig.
        // TODO: Reconsider when NightConfig supports waiting for the watcher registration.
        Thread.sleep(250);

        Files.writeString(tempDir.resolve(modConfig.getFileName()), """
                configEntry = 5invalidintegerliteral heh
                """);

        waitUntil(() -> {
            Assertions.assertThat(spec.loadedValue)
                    .isEqualTo(4);
            Assertions.assertThat(modConfig.getFullPath())
                    .exists()
                    .hasContent("""
                            #Test comment:
                            configEntry = 4
                            """);
        });
    }

    @Test
    void testValidation() {
        var spec = new SimpleConfigSpec() {
            @Override
            public void validateSpec(ModConfig config) {
                throw new IllegalArgumentException("Config is bad.");
            }
        };
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> configTracker.registerConfig(ModConfig.Type.COMMON, spec, modContainer, "test.toml"));
    }

    private void waitUntil(Runnable assertion) throws InterruptedException {
        for (int i = 0; i < 1000; ++i) {
            try {
                assertion.run();
                return;
            } catch (AssertionError ignored) {
                Thread.sleep(10);
            }
        }

        assertion.run();
    }

    private static class SimpleConfigSpec implements IConfigSpec {
        private int loadedValue = 4;

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void validateSpec(ModConfig config) {}

        @Override
        public boolean isCorrect(UnmodifiableCommentedConfig config) {
            return correct(config, true);
        }

        @Override
        public void correct(CommentedConfig config) {
            correct(config, false);
        }

        private boolean correct(UnmodifiableCommentedConfig config, boolean dryRun) {
            boolean ok = true;
            if (!config.contains("configEntry") || !(config.get("configEntry") instanceof Integer)) {
                ok = false;
                if (!dryRun) {
                    ((CommentedConfig) config).set("configEntry", 4);
                }
            }
            if (!dryRun) {
                if (!config.containsComment("configEntry")) {
                    ((CommentedConfig) config).setComment("configEntry", "Test comment:");
                }
            }
            return ok;
        }

        @Override
        public void acceptConfig(@Nullable ILoadedConfig config) {
            loadedValue = config == null ? 4 : config.config().getInt("configEntry");
        }
    }
}
