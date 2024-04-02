/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ServiceRunner;
import cpw.mods.niofs.union.UnionPathFilter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FileUtils;
import net.neoforged.fml.loading.LogMarkers;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;

public abstract class CommonLaunchHandler implements ILaunchHandlerService {
    public record LocatedPaths(List<Path> minecraftPaths, UnionPathFilter minecraftFilter, List<List<Path>> otherModPaths, List<Path> otherArtifacts) {}

    protected static final Logger LOGGER = LogUtils.getLogger();

    public abstract Dist getDist();

    public boolean isProduction() {
        return false;
    }

    public boolean isData() {
        return false;
    }

    public abstract LocatedPaths getMinecraftPaths();

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

    protected final String[] getLegacyClasspath() {
        return Objects.requireNonNull(System.getProperty("legacyClassPath"), "Missing legacyClassPath, cannot load").split(File.pathSeparator);
    }

    protected final List<Path> getFmlPaths(String[] classpath) {
        String[] fmlLibraries = System.getProperty("fml.pluginLayerLibraries").split(",");
        return Arrays.stream(classpath)
                .filter(e -> FileUtils.matchFileName(e, true, fmlLibraries))
                .map(Paths::get)
                .toList();
    }

    public static Map<String, List<Path>> getModClasses() {
        final String modClasses = Optional.ofNullable(System.getenv("MOD_CLASSES")).orElse("");
        LOGGER.debug(LogMarkers.CORE, "Got mod coordinates {} from env", modClasses);

        record ExplodedModPath(String modid, Path path) {}
        // "a/b/;c/d/;" -> "modid%%c:\fish\pepper;modid%%c:\fish2\pepper2\;modid2%%c:\fishy\bums;modid2%%c:\hmm"
        final var modClassPaths = Arrays.stream(modClasses.split(File.pathSeparator))
                .map(inp -> inp.split("%%", 2))
                .map(splitString -> new ExplodedModPath(splitString.length == 1 ? "defaultmodid" : splitString[0], Paths.get(splitString[splitString.length - 1])))
                .collect(Collectors.groupingBy(ExplodedModPath::modid, Collectors.mapping(ExplodedModPath::path, Collectors.toList())));

        LOGGER.debug(LogMarkers.CORE, "Found supplied mod coordinates [{}]", modClassPaths);

        //final var explodedTargets = ((Map<String, List<ExplodedDirectoryLocator.ExplodedMod>>)arguments).computeIfAbsent("explodedTargets", a -> new ArrayList<>());
        //modClassPaths.forEach((modlabel,paths) -> explodedTargets.add(new ExplodedDirectoryLocator.ExplodedMod(modlabel, paths)));
        return modClassPaths;
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

    protected void dataService(final String[] arguments, final ModuleLayer layer) throws Throwable {
        runTarget("net.minecraft.data.Main", arguments, layer);
    }

    protected void runTarget(final String target, final String[] arguments, final ModuleLayer layer) throws Throwable {
        Class.forName(layer.findModule("minecraft").orElseThrow(), target).getMethod("main", String[].class).invoke(null, (Object) arguments);
    }
}
