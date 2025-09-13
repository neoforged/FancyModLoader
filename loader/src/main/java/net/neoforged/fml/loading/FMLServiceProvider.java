/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.neoforged.neoforgespi.Environment;
import net.neoforged.neoforgespi.ILaunchContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

@ApiStatus.Internal
public class FMLServiceProvider {
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

    public List<Resource> beginScanning(final IEnvironment environment) {
        LOGGER.debug(CORE, "Initiating mod scan");
        return FMLLoader.beginModScan(launchContext);
    }

    public List<Resource> completeScan(final IModuleLayerManager layerManager) {
        return FMLLoader.completeScan(launchContext, mixinConfigsArgumentList);
    }

    public ILaunchContext getLaunchContext() {
        return this.launchContext;
    }

    public record Resource(IModuleLayerManager.Layer target, List<SecureJar> resources) {}

    public void onLoad(IEnvironment environment) {
        try {
            FMLLoader.onInitialLoad(environment);
        } catch (IncompatibleEnvironmentException e) {
            LOGGER.error("FML failed to load", e);
            throw new IllegalStateException(e);
        }
    }

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

    public interface OptionResult {
        <V> V value(OptionSpec<V> options);

        <V> List<V> values(OptionSpec<V> options);
    }

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
}
