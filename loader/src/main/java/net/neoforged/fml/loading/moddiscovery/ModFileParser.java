/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.concurrent.ConcurrentConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.InvalidModFileException;
import net.neoforged.neoforgespi.locating.ModFileInfoParser;
import org.slf4j.Logger;

public class ModFileParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static IModFileInfo readModList(final ModFile modFile, final ModFileInfoParser parser) {
        return parser.build(modFile);
    }

    public static IModFileInfo modsTomlParser(final IModFile imodFile) {
        ModFile modFile = (ModFile) imodFile;
        LOGGER.debug(LogMarkers.LOADING, "Considering mod file candidate {}", modFile.getFilePath());
        final Path modsjson = modFile.findResource(JarModsDotTomlModFileReader.MODS_TOML);
        if (!Files.exists(modsjson)) {
            LOGGER.warn(LogMarkers.LOADING, "Mod file {} is missing {} file", modFile.getFilePath(), JarModsDotTomlModFileReader.MODS_TOML);
            return null;
        }

        final FileConfig fileConfig = FileConfig.builder(modsjson).build();
        fileConfig.load();
        fileConfig.close();
        // Make an immutable copy of the config. A FileConfig is a ConcurrentConfig,
        // and we don't want to leak the complexities of ConcurrentConfigs
        // (such as not supporting `valueMap`) into this read-only code.
        final NightConfigWrapper configWrapper = new NightConfigWrapper(copyConfig(fileConfig));
        return new ModFileInfo(modFile, configWrapper, configWrapper::setFile);
    }

    /**
     * Creates an immutable copy of a concurrent config.
     */
    private static UnmodifiableConfig copyConfig(ConcurrentConfig config) {
        var format = TomlFormat.instance();
        // The best I could do given that Config.copy(...) only performs a shallow copy,
        // and does not work for StampedConfigs anyway.
        return format.createParser().parse(format.createWriter().writeToString(config)).unmodifiable();
    }

    protected static List<CoreModFile> getCoreMods(final ModFile modFile) {
        Map<String, String> coreModPaths;
        try {
            final Path coremodsjson = modFile.findResource("META-INF", "coremods.json");
            if (!Files.exists(coremodsjson)) {
                return Collections.emptyList();
            }
            final Type type = new TypeToken<Map<String, String>>() {}.getType();
            final Gson gson = new Gson();
            coreModPaths = gson.fromJson(Files.newBufferedReader(coremodsjson), type);
        } catch (IOException e) {
            LOGGER.debug(LogMarkers.LOADING, "Failed to read coremod list coremods.json", e);
            return Collections.emptyList();
        }

        return coreModPaths.entrySet().stream()
                .peek(e -> LOGGER.debug(LogMarkers.LOADING, "Found coremod {} with Javascript path {}", e.getKey(), e.getValue()))
                .map(e -> new CoreModFile(e.getKey(), modFile.findResource(e.getValue()), modFile))
                .toList();
    }

    /**
     * Represents a potential mixin configuration.
     *
     * @param config       The name of the mixin configuration.
     * @param requiredMods The mod ids that are required for this mixin configuration to be loaded. If empty, will be loaded regardless.
     */
    public record MixinConfig(String config, List<String> requiredMods) {}

    protected static List<MixinConfig> getMixinConfigs(IModFileInfo modFileInfo) {
        try {
            var config = modFileInfo.getConfig();
            var mixinsEntries = config.getConfigList("mixins");

            var potentialMixins = new ArrayList<MixinConfig>();
            for (IConfigurable mixinsEntry : mixinsEntries) {
                var name = mixinsEntry.<String>getConfigElement("config")
                        .orElseThrow(() -> new InvalidModFileException("Missing \"config\" in [[mixins]] entry", modFileInfo));
                var requiredModIds = mixinsEntry.<List<String>>getConfigElement("requiredMods").orElse(List.of());
                potentialMixins.add(new MixinConfig(name, requiredModIds));
            }

            return potentialMixins;
        } catch (Exception exception) {
            LOGGER.error("Failed to load mixin configs from mod file", exception);
            return List.of();
        }
    }

    protected static Optional<List<String>> getAccessTransformers(IModFileInfo modFileInfo) {
        try {
            final var config = modFileInfo.getConfig();
            final var atEntries = config.getConfigList("accessTransformers");
            if (atEntries.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(atEntries
                    .stream()
                    .map(entry -> entry
                            .<String>getConfigElement("file")
                            .orElseThrow(
                                    () -> new InvalidModFileException("Missing \"file\" in [[accessTransformers]] entry", modFileInfo)))
                    .toList());
        } catch (Exception exception) {
            LOGGER.error("Failed to load access transformers from mod file", exception);
            return Optional.of(List.of());
        }
    }
}
