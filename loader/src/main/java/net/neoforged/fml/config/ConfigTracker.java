/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@ApiStatus.Internal
public class ConfigTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Marker CONFIG = MarkerFactory.getMarker("CONFIG");
    public static final ConfigTracker INSTANCE = new ConfigTracker();
    private static final Path defaultConfigPath = FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath());

    final ConcurrentHashMap<String, ModConfig> fileMap;
    final EnumMap<ModConfig.Type, Set<ModConfig>> configSets;
    final ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>> configsByMod;

    @VisibleForTesting
    public ConfigTracker() {
        this.fileMap = new ConcurrentHashMap<>();
        this.configSets = new EnumMap<>(ModConfig.Type.class);
        this.configsByMod = new ConcurrentHashMap<>();
        this.configSets.put(ModConfig.Type.CLIENT, Collections.synchronizedSet(new LinkedHashSet<>()));
        this.configSets.put(ModConfig.Type.COMMON, Collections.synchronizedSet(new LinkedHashSet<>()));
        this.configSets.put(ModConfig.Type.SERVER, Collections.synchronizedSet(new LinkedHashSet<>()));
        this.configSets.put(ModConfig.Type.STARTUP, Collections.synchronizedSet(new LinkedHashSet<>()));
    }

    public ModConfig registerConfig(ModConfig.Type type, IConfigSpec spec, ModContainer container) {
        return registerConfig(type, spec, container, defaultConfigName(type, container.getModId()));
    }

    public ModConfig registerConfig(ModConfig.Type type, IConfigSpec spec, ModContainer container, String fileName) {
        var modConfig = new ModConfig(type, spec, container, fileName);
        trackConfig(modConfig);
        return modConfig;
    }

    private static String defaultConfigName(ModConfig.Type type, String modId) {
        // config file name would be "forge-client.toml" and "forge-server.toml"
        return String.format(Locale.ROOT, "%s-%s.toml", modId, type.extension());
    }

    void trackConfig(final ModConfig config) {
        if (this.fileMap.containsKey(config.getFileName())) {
            LOGGER.error(CONFIG, "Detected config file conflict {} between {} and {}", config.getFileName(), this.fileMap.get(config.getFileName()).getModId(), config.getModId());
            throw new RuntimeException("Config conflict detected!");
        }
        this.fileMap.put(config.getFileName(), config);
        this.configSets.get(config.getType()).add(config);
        this.configsByMod.computeIfAbsent(config.getModId(), (k) -> new EnumMap<>(ModConfig.Type.class)).put(config.getType(), config);
        LOGGER.debug(CONFIG, "Config file {} for {} tracking", config.getFileName(), config.getModId());
    }

    public void loadConfigs(ModConfig.Type type, Path configBasePath) {
        loadConfigs(type, configBasePath, null);
    }

    public void loadConfigs(ModConfig.Type type, Path configBasePath, @Nullable Path configOverrideBasePath) {
        LOGGER.debug(CONFIG, "Loading configs type {}", type);
        this.configSets.get(type).forEach(config -> openConfig(config, configBasePath, configOverrideBasePath));
    }

    public void unloadConfigs(ModConfig.Type type) {
        LOGGER.debug(CONFIG, "Unloading configs type {}", type);
        this.configSets.get(type).forEach(this::closeConfig);
    }

    public void openConfig(final ModConfig config, final Path configBasePath, @Nullable Path configOverrideBasePath) {
        LOGGER.trace(CONFIG, "Loading config file type {} at {} for {}", config.getType(), config.getFileName(), config.getModId());
        if (config.config != null) {
            LOGGER.warn("Opening a config that was already loaded with value {} at path {}", config.config, config.getFileName());
        }
        final Path basePath = resolveBasePath(config, configBasePath, configOverrideBasePath);
        final Path configPath = basePath.resolve(config.getFileName());
        final CommentedFileConfig configData = CommentedFileConfig.builder(configPath)
                .sync()
                .preserveInsertionOrder()
                .autosave()
                .onFileNotFound((newfile, configFormat) -> setupConfigFile(config, newfile, configFormat))
                .writingMode(WritingMode.REPLACE_ATOMIC)
                .build();
        LOGGER.debug(CONFIG, "Built TOML config for {}", configPath);

        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DISABLE_CONFIG_WATCHER)) {
            FileWatcher.defaultInstance().addWatch(configPath, new ConfigWatcher(config, configData, Thread.currentThread().getContextClassLoader()));
            LOGGER.debug(CONFIG, "Watching TOML config file {} for changes", configPath);
        }

        loadConfig(config, configData);
        LOGGER.debug(CONFIG, "Loaded TOML config file {}", configPath);
        config.config = configData;
        config.postConfigEvent(ModConfigEvent.Loading::new);
    }

    private Path resolveBasePath(ModConfig config, Path configBasePath, @Nullable Path configOverrideBasePath) {
        if (configOverrideBasePath != null) {
            Path overrideFilePath = configOverrideBasePath.resolve(config.getFileName());
            if (Files.exists(overrideFilePath)) {
                LOGGER.info(CONFIG, "Found config file override in path {}", overrideFilePath);
                return configOverrideBasePath;
            }
        }
        return configBasePath;
    }

    static void loadConfig(ModConfig modConfig, CommentedFileConfig config) {
        try {
            config.load();
            if (!modConfig.getSpec().isCorrect(config)) {
                LOGGER.warn(CONFIG, "Configuration file {} is not correct. Correcting", config.getFile().getAbsolutePath());
                backUpConfig(config);
                modConfig.getSpec().correct(config);
                config.save();
            }
        } catch (ParsingException ex) {
            LOGGER.warn(CONFIG, "Failed to parse {}: {}. Attempting to recreate", modConfig.getFileName(), ex);
            try {
                backUpConfig(config);
                Files.delete(config.getNioPath());

                setupConfigFile(modConfig, config.getNioPath(), config.configFormat());
                config.load();
            } catch (Throwable t) {
                ex.addSuppressed(t);

                throw new RuntimeException("Failed to recreate config file " + modConfig.getFileName() + " of type " + modConfig.getType() + " for modid " + modConfig.getModId(), ex);
            }
        }
        modConfig.getSpec().load(config);
    }

    public void acceptSyncedConfig(ModConfig modConfig, byte[] bytes) {
        if (modConfig.config != null) {
            LOGGER.warn("Overwriting non-null config {} at path {} with synced config", modConfig.config, modConfig.getFileName());
        }
        modConfig.config = TomlFormat.instance().createParser().parse(new ByteArrayInputStream(bytes));
        // TODO: do we want to do any validation? (what do we do if it fails?)
        modConfig.getSpec().load(modConfig.config);
        modConfig.postConfigEvent(ModConfigEvent.Reloading::new); // TODO: should maybe be Loading on the first load?
    }

    public void loadDefaultServerConfigs() {
        configSets.get(ModConfig.Type.SERVER).forEach(modConfig -> {
            if (modConfig.config != null) {
                LOGGER.warn("Overwriting non-null config {} at path {} with default server config", modConfig.config, modConfig.getFileName());
            }
            modConfig.config = CommentedConfig.copy(modConfig.getSpec().getDefaultConfig());
            modConfig.getSpec().load(modConfig.config);
            modConfig.postConfigEvent(ModConfigEvent.Loading::new);
        });
    }

    private void closeConfig(final ModConfig config) {
        if (config.config != null) {
            if (config.config instanceof CommentedFileConfig) {
                LOGGER.trace(CONFIG, "Closing config file type {} at {} for {}", config.getType(), config.getFileName(), config.getModId());
                unload(config);
                config.config = null;
                config.postConfigEvent(ModConfigEvent.Unloading::new);
            } else {
                LOGGER.warn(CONFIG, "Closing non-file config {} at path {}", config.config, config.getFileName());
            }
        }
    }

    private static void unload(ModConfig config) {
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DISABLE_CONFIG_WATCHER))
            return;
        Path configPath = config.getFullPath();
        try {
            FileWatcher.defaultInstance().removeWatch(configPath);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to remove config {} from tracker!", configPath, e);
        }
    }

    private static boolean setupConfigFile(final ModConfig modConfig, final Path file, final ConfigFormat<?> conf) throws IOException {
        Files.createDirectories(file.getParent());
        Path p = defaultConfigPath.resolve(modConfig.getFileName());
        if (Files.exists(p)) {
            LOGGER.info(CONFIG, "Loading default config file from path {}", p);
            Files.copy(p, file);
        } else {
            conf.createWriter().write(modConfig.getSpec().getDefaultConfig(), file, WritingMode.REPLACE_ATOMIC);
        }
        return true;
    }

    private static void backUpConfig(final CommentedFileConfig commentedFileConfig) {
        backUpConfig(commentedFileConfig, 5); //TODO: Think of a way for mods to set their own preference (include a sanity check as well, no disk stuffing)
    }

    private static void backUpConfig(final CommentedFileConfig commentedFileConfig, final int maxBackups) {
        backUpConfig(commentedFileConfig.getNioPath(), maxBackups);
    }

    private static void backUpConfig(final Path commentedFileConfig, final int maxBackups) {
        Path bakFileLocation = commentedFileConfig.getParent();
        String bakFileName = FilenameUtils.removeExtension(commentedFileConfig.getFileName().toString());
        String bakFileExtension = FilenameUtils.getExtension(commentedFileConfig.getFileName().toString()) + ".bak";
        Path bakFile = bakFileLocation.resolve(bakFileName + "-1" + "." + bakFileExtension);
        try {
            for (int i = maxBackups; i > 0; i--) {
                Path oldBak = bakFileLocation.resolve(bakFileName + "-" + i + "." + bakFileExtension);
                if (Files.exists(oldBak)) {
                    if (i >= maxBackups)
                        Files.delete(oldBak);
                    else
                        Files.move(oldBak, bakFileLocation.resolve(bakFileName + "-" + (i + 1) + "." + bakFileExtension));
                }
            }
            Files.copy(commentedFileConfig, bakFile);
        } catch (IOException exception) {
            LOGGER.warn(CONFIG, "Failed to back up config file {}", commentedFileConfig, exception);
        }
    }
}
