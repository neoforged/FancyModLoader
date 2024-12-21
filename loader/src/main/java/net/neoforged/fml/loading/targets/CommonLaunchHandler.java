/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ServiceRunner;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
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
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;

public abstract class CommonLaunchHandler implements ILaunchHandlerService {
    protected static final Logger LOGGER = LogUtils.getLogger();

    public abstract Dist getDist();

    public abstract boolean isProduction();

    /**
     * Return additional locators to be used for locating mods when this launch handler is used.
     */
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {}

    protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
        // In dev, do not overwrite the logging configuration if the user explicitly set another one.
        // In production, always overwrite the vanilla configuration.
        if (isProduction() || System.getProperty("log4j2.configurationFile") == null) {
            overwriteLoggingConfiguration(layer);
        }

        return arguments;
    }

    /**
     * Forces the log4j2 logging context to use the configuration shipped with fml_loader.
     */
    private void overwriteLoggingConfiguration(final ModuleLayer layer) {
        URI uri;
        try (var reader = layer.configuration().findModule("fml_loader").orElseThrow().reference().open()) {
            uri = reader.find("log4j2.xml").orElseThrow();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Configurator.reconfigure(ConfigurationFactory.getInstance().getConfiguration(LoggerContext.getContext(), ConfigurationSource.fromUri(uri)));
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

    @Override
    public ServiceRunner launchService(final String[] arguments, final ModuleLayer gameLayer) {
        FMLLoader.beforeStart(gameLayer);
        var args = preLaunch(arguments, gameLayer);

        return () -> runService(args, gameLayer);
    }

    protected abstract void runService(final String[] arguments, final ModuleLayer gameLayer) throws Throwable;

    protected void clientService(final String[] arguments, final ModuleLayer layer) throws Throwable {
        runTarget("net.minecraft.client.main.Main", arguments, layer);
    }

    protected void serverService(final String[] arguments, final ModuleLayer layer) throws Throwable {
        runTarget("net.minecraft.server.Main", arguments, layer);
    }

    protected void runTarget(final String target, final String[] arguments, final ModuleLayer layer) throws Throwable {
        Class.forName(layer.findModule("minecraft").orElseThrow(), target).getMethod("main", String[].class).invoke(null, (Object) arguments);
    }
}
