/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.slf4j.Logger;

public abstract class CommonLaunchHandler {
    public abstract String name();

    protected static final Logger LOGGER = LogUtils.getLogger();

    public abstract Dist getDist();

    public abstract boolean isProduction();

    /**
     * Return additional locators to be used for locating mods when this launch handler is used.
     */
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {}

    protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
        return arguments;
    }

    public static Map<String, List<Path>> getGroupedModFolders() {
        Map<String, List<Path>> result;

        var modFolders = Optional.ofNullable(System.getenv("MOD_CLASSES"))
                .orElse(System.getProperty("fml.modFolders", ""));
        var modFoldersFile = System.getProperty("fml.modFoldersFile", "");
        if (!modFoldersFile.isEmpty()) {
            LOGGER.debug(LogMarkers.CORE, "Reading additional mod folders from file {}", modFoldersFile);
            var p = new Properties();
            try (var in = Files.newBufferedReader(Paths.get(modFoldersFile))) {
                p.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read mod classes list from " + modFoldersFile, e);
            }

            result = p.stringPropertyNames()
                    .stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            modId -> Arrays.stream(p.getProperty(modId).split(File.pathSeparator)).map(Paths::get).toList()));
        } else if (!modFolders.isEmpty()) {
            LOGGER.debug(LogMarkers.CORE, "Got mod coordinates {} from env", modFolders);
            record ExplodedModPath(String modId, Path path) {}
            // "a/b/;c/d/;" -> "modid%%c:\fish\pepper;modid%%c:\fish2\pepper2\;modid2%%c:\fishy\bums;modid2%%c:\hmm"
            result = Arrays.stream(modFolders.split(File.pathSeparator))
                    .map(inp -> inp.split("%%", 2))
                    .map(splitString -> new ExplodedModPath(splitString.length == 1 ? "defaultmodid" : splitString[0], Paths.get(splitString[splitString.length - 1])))
                    .collect(Collectors.groupingBy(ExplodedModPath::modId, Collectors.mapping(ExplodedModPath::path, Collectors.toList())));
        } else {
            result = Map.of();
        }

        LOGGER.debug(LogMarkers.CORE, "Found supplied mod coordinates [{}]", result);
        return result;
    }

    protected abstract void runService(final String[] arguments, final ModuleLayer gameLayer) throws Throwable;

    protected void clientService(final String[] arguments, final ModuleLayer layer) throws Throwable {
        runTarget("net.minecraft.client.main.Main", arguments, layer);
    }

    protected void serverService(final String[] arguments, final ModuleLayer layer) throws Throwable {
        runTarget("net.minecraft.server.Main", arguments, layer);
    }

    protected void runTarget(final String target, final String[] arguments, final ModuleLayer layer) throws Throwable {
        try {
            Class.forName(layer.findModule("minecraft").orElseThrow(), target).getMethod("main", String[].class).invoke(null, (Object) arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
