/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.niofs.union.UnionFileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
                            locatedPaths.add(unpackPath(Paths.get(moduleUri)));
                        } catch (Exception ignored) {}
                    });
                }
            });
        }
        LOG.debug(LogMarkers.SCAN, "Located paths when launch context was created: {}", locatedPaths);
    }

    private Path unpackPath(Path path) {
        if (path.getFileSystem() instanceof UnionFileSystem unionFileSystem) {
            return unionFileSystem.getPrimaryPath();
        }
        return path;
    }

    @Override
    public <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass) {
        var visitedLayers = EnumSet.noneOf(IModuleLayerManager.Layer.class);

        Stream<ServiceLoader.Provider<T>> result = Stream.empty();

        var layers = IModuleLayerManager.Layer.values();
        for (int i = layers.length - 1; i >= 0; i--) {
            var layerId = layers[i];
            if (!visitedLayers.contains(layerId)) {
                var layer = moduleLayerManager.getLayer(layerId).orElse(null);
                if (layer != null) {
                    result = Stream.concat(result, ServiceLoader.load(layer, serviceClass).stream());

                    // Services loaded from this layer also include services from the parent layers
                    visitLayer(layerId, visitedLayers::add);
                }
            }
        }

        return result.distinct();
    }

    private static void visitLayer(IModuleLayerManager.Layer layer, Consumer<IModuleLayerManager.Layer> consumer) {
        consumer.accept(layer);
        for (var parentLayer : layer.getParent()) {
            consumer.accept(parentLayer);
        }
    }

    @Override
    public boolean isLocated(Path path) {
        return locatedPaths.contains(unpackPath(path));
    }

    public boolean addLocated(Path path) {
        return locatedPaths.add(unpackPath(path));
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
