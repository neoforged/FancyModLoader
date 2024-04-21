/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import net.neoforged.neoforgespi.ILaunchContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LaunchContext implements ILaunchContext {
    private static final Logger LOG = LoggerFactory.getLogger(LaunchContext.class);
    private final IEnvironment environment;
    private final IModuleLayerManager moduleLayerManager;
    private final List<String> modLists;
    private final List<String> mods;
    private final List<String> mavenRoots;
    private final Set<Path> locatedPaths = new HashSet<>();

    LaunchContext(
            IEnvironment environment,
            IModuleLayerManager moduleLayerManager,
            List<String> modLists,
            List<String> mods,
            List<String> mavenRoots) {
        this.environment = environment;
        this.moduleLayerManager = moduleLayerManager;
        this.modLists = modLists;
        this.mods = mods;
        this.mavenRoots = mavenRoots;

        // Index current layers of the module layer manager
        for (var layerId : IModuleLayerManager.Layer.values()) {
            moduleLayerManager.getLayer(layerId).ifPresent(layer -> {
                for (var resolvedModule : layer.configuration().modules()) {
                    resolvedModule.reference().location().ifPresent(moduleUri -> {
                        try {
                            locatedPaths.add(Paths.get(moduleUri));
                        } catch (Exception ignored) {}
                    });
                }
            });
        }
        LOG.debug(LogMarkers.SCAN, "Located paths when launch context was created: {}", locatedPaths);
    }

    @Override
    public <T> ServiceLoader<T> createServiceLoader(Class<T> serviceClass) {
        var moduleLayer = moduleLayerManager.getLayer(IModuleLayerManager.Layer.SERVICE).orElseThrow();
        return ServiceLoader.load(moduleLayer, serviceClass);
    }

    @Override
    public boolean isLocated(Path path) {
        return locatedPaths.contains(path);
    }

    public boolean addLocated(Path path) {
        return locatedPaths.add(path);
    }

    @Override
    public IEnvironment environment() {
        return environment;
    }

    public IModuleLayerManager moduleLayerManager() {
        return moduleLayerManager;
    }

    @Override
    public List<String> modLists() {
        return modLists;
    }

    @Override
    public List<String> mods() {
        return mods;
    }

    @Override
    public List<String> mavenRoots() {
        return mavenRoots;
    }
}
