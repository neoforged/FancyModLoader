/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSpecBuilder;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.neoforgespi.Environment;
import net.neoforged.neoforgespi.ILaunchContext;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static net.neoforged.fml.loading.LogMarkers.CORE;

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
    @VisibleForTesting
    ILaunchContext launchContext;

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
        LOGGER.debug(CORE, "Preparing launch handler");
        var launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("MISSING");
        final Optional<ILaunchHandlerService> launchHandler = environment.findLaunchHandler(launchTarget);
        LOGGER.debug(LogMarkers.CORE, "Using {} as launch service", launchTarget);
        if (launchHandler.isEmpty()) {
            LOGGER.error(LogMarkers.CORE, "Missing LaunchHandler {}, cannot continue", launchTarget);
            throw new RuntimeException("Missing launch handler: " + launchTarget);
        }

        if (!(launchHandler.get() instanceof CommonLaunchHandler commonLaunchHandler)) {
            LOGGER.error(LogMarkers.CORE, "Incompatible Launch handler found - type {}, cannot continue", launchHandler.get().getClass().getName());
            throw new RuntimeException("Incompatible launch handler found");
        }
        var moduleLayerManager = environment.findModuleLayerManager().orElseThrow();
        launchContext = new LaunchContext(
                environment,
                commonLaunchHandler.getDist(),
                FMLPaths.GAMEDIR.get(),
                moduleLayerManager,
                modListsArgumentList,
                modsArgumentList,
                mavenRootsArgumentList,
                List.of()
        );
        FMLLoader.setupLaunchHandler(versionInfo, commonLaunchHandler);
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
        return FMLLoader.getCoreModTransformers(launchContext);
    }
}
