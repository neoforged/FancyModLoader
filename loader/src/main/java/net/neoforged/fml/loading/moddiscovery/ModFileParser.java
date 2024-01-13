/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.coremod.ICoreModFile;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModFileParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static IModFileInfo readModList(final ModFile modFile, final ModFileFactory.ModFileInfoParser parser) {
        return parser.build(modFile);
    }

    public static IModFileInfo modsTomlParser(final IModFile imodFile) {
        ModFile modFile = (ModFile) imodFile;
        LOGGER.debug(LogMarkers.LOADING,"Considering mod file candidate {}", modFile.getFilePath());
        final Path modsjson = modFile.findResource("META-INF", "mods.toml");
        if (!Files.exists(modsjson)) {
            LOGGER.warn(LogMarkers.LOADING, "Mod file {} is missing mods.toml file", modFile.getFilePath());
            return null;
        }

        final FileConfig fileConfig = FileConfig.builder(modsjson).build();
        fileConfig.load();
        fileConfig.close();
        final NightConfigWrapper configWrapper = new NightConfigWrapper(fileConfig);
        return new ModFileInfo(modFile, configWrapper, configWrapper::setFile);
    }

    protected static List<ICoreModFile> getCoreMods(final ModFile modFile) {
        Map<String,String> coreModPaths;
        try {
            final Path coremodsjson = modFile.findResource("META-INF", "coremods.json");
            if (!Files.exists(coremodsjson)) {
                return Collections.emptyList();
            }
            final Type type = new TypeToken<Map<String, String>>() {}.getType();
            final Gson gson = new Gson();
            coreModPaths = gson.fromJson(Files.newBufferedReader(coremodsjson), type);
        } catch (IOException e) {
            LOGGER.debug(LogMarkers.LOADING,"Failed to read coremod list coremods.json", e);
            return Collections.emptyList();
        }

        return coreModPaths.entrySet().stream()
                .peek(e-> LOGGER.debug(LogMarkers.LOADING,"Found coremod {} with Javascript path {}", e.getKey(), e.getValue()))
                .<ICoreModFile>map(e -> new CoreModFile(e.getKey(), modFile.findResource(e.getValue()),modFile))
                .toList();
    }

    protected static List<String> getMixinConfigs(IModFileInfo modFileInfo) {
        try {
            var config = modFileInfo.getConfig();
            var mixinsEntries = config.getConfigList("mixins");
            return mixinsEntries
                    .stream()
                    .map(entry -> entry
                            .<String>getConfigElement("config")
                            .orElseThrow(
                                    () -> new InvalidModFileException("Missing \"config\" in [[mixins]] entry", modFileInfo)))
                    .toList();
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
