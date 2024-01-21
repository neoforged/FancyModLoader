/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSpecBuilder;
import net.neoforged.neoforgespi.Environment;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.slf4j.Logger;

public class FMLServiceProvider implements ITransformationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ArgumentAcceptingOptionSpec<String> modsOption;
    private ArgumentAcceptingOptionSpec<String> modListsOption;
    private ArgumentAcceptingOptionSpec<String> mavenRootsOption;
    private ArgumentAcceptingOptionSpec<String> mixinConfigsOption;
    private ArgumentAcceptingOptionSpec<String> fmlOption;
    private ArgumentAcceptingOptionSpec<String> forgeOption;
    private ArgumentAcceptingOptionSpec<String> mcOption;
    private ArgumentAcceptingOptionSpec<String> mcpOption;
    private List<String> modsArgumentList;
    private List<String> modListsArgumentList;
    private List<String> mavenRootsArgumentList;
    private List<String> mixinConfigsArgumentList;
    private VersionInfo versionInfo;
    private ILaunchContext launchContext;

    public FMLServiceProvider() {
        final String markerselection = System.getProperty("forge.logging.markers", "");
        Arrays.stream(markerselection.split(",")).forEach(marker -> System.setProperty("forge.logging.marker." + marker.toLowerCase(Locale.ROOT), "ACCEPT"));
    }

    @Override
    public String name() {
        return "fml";
    }

    @Override
    public void initialize(IEnvironment environment) {
        LOGGER.debug(CORE, "Setting up basic FML game directories");
        FMLPaths.setup(environment);
        LOGGER.debug(CORE, "Loading configuration");
        FMLConfig.load();
        var moduleLayerManager = environment.findModuleLayerManager().orElseThrow();
        launchContext = new LaunchContext(environment,
                moduleLayerManager,
                modListsArgumentList,
                modsArgumentList,
                mavenRootsArgumentList);
        LOGGER.debug(CORE, "Preparing launch handler");
        FMLLoader.setupLaunchHandler(environment, versionInfo);
        FMLEnvironment.setupInteropEnvironment(environment);
        Environment.build(environment);
    }

    @Override
    public List<Resource> beginScanning(final IEnvironment environment) {
        LOGGER.debug(CORE, "Initiating mod scan");
        return FMLLoader.beginModScan(launchContext);
    }

    @Override
    public List<Resource> completeScan(final IModuleLayerManager layerManager) {
        Supplier<ModuleLayer> gameLayerSupplier = () -> layerManager.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow();
        return FMLLoader.completeScan(launchContext, mixinConfigsArgumentList);
    }

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException {
        FMLLoader.onInitialLoad(environment);
    }

    @Override
    public void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder) {
        forgeOption = argumentBuilder.apply("neoForgeVersion", "NeoForge Version number").withRequiredArg().ofType(String.class).required();
        fmlOption = argumentBuilder.apply("fmlVersion", "FML Version number").withRequiredArg().ofType(String.class).required();
        mcOption = argumentBuilder.apply("mcVersion", "Minecraft Version number").withRequiredArg().ofType(String.class).required();
        mcpOption = argumentBuilder.apply("neoFormVersion", "Neoform Version number").withRequiredArg().ofType(String.class).required();
        modsOption = argumentBuilder.apply("mods", "List of mods to add").withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
        modListsOption = argumentBuilder.apply("modLists", "JSON modlists").withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
        mavenRootsOption = argumentBuilder.apply("mavenRoots", "Maven root directories").withRequiredArg().ofType(String.class).withValuesSeparatedBy(",");
        mixinConfigsOption = argumentBuilder.apply("mixinConfig", "Additional mixin config files to load").withRequiredArg().ofType(String.class);
    }

    @Override
    public void argumentValues(OptionResult option) {
        modsArgumentList = option.values(modsOption);
        modListsArgumentList = option.values(modListsOption);
        mavenRootsArgumentList = option.values(mavenRootsOption);
        mixinConfigsArgumentList = option.values(mixinConfigsOption);
        versionInfo = new VersionInfo(
                option.value(forgeOption),
                option.value(fmlOption),
                option.value(mcOption),
                option.value(mcpOption));
        LOGGER.debug(LogMarkers.CORE, "Received command line version data  : {}", versionInfo);
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        LOGGER.debug(CORE, "Loading coremod transformers");

        var result = new ArrayList<>(loadCoreModScripts());

        // Find all Java core mods
        var pluginLayer = Launcher.INSTANCE.findLayerManager()
                .flatMap(m -> m.getLayer(IModuleLayerManager.Layer.PLUGIN))
                .orElseThrow();
        for (var coreMod : ServiceLoader.load(pluginLayer, ICoreMod.class)) {
            for (var transformer : coreMod.getTransformers()) {
                if (transformer == null) {
                    throw new IllegalStateException("Core mod " + coreMod + " is trying to add null transformer");
                }
                LOGGER.debug(CORE, "Adding {} transformer from core-mod {}", transformer.targets(), coreMod);
                result.add(transformer);
            }
        }

        return result;
    }

    private List<ITransformer<?>> loadCoreModScripts() {
        var filesWithCoreModScripts = LoadingModList.get().getModFiles()
                .stream()
                .filter(mf -> !mf.getFile().getCoreMods().isEmpty())
                .toList();

        if (filesWithCoreModScripts.isEmpty()) {
            // Don't even bother starting the scripting engine if no mod contains scripting core mods
            LOGGER.debug(LogMarkers.CORE, "Not loading coremod script-engine since no mod requested it");
            return Collections.emptyList();
        }

        LOGGER.info(LogMarkers.CORE, "Loading coremod script-engine for {}", filesWithCoreModScripts);
        try {
            return CoreModScriptLoader.loadCoreModScripts(filesWithCoreModScripts);
        } catch (NoClassDefFoundError e) {
            var message = "Could not find the coremod script-engine, but the following mods require it: " + filesWithCoreModScripts;
            ImmediateWindowHandler.crash(message);
            throw new IllegalStateException(message, e);
        }
    }
}
