/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;
import net.neoforged.accesstransformer.ml.AccessTransformerService;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import org.jetbrains.annotations.Nullable;

public class TestEnvironment implements IEnvironment {
    private final TypesafeMap map = new TypesafeMap(IEnvironment.class);
    private final TestModuleLayerManager moduleLayerManager;
    @Nullable
    public AccessTransformerService accessTransformerService = new AccessTransformerService();
    @Nullable
    public RuntimeDistCleaner runtimeDistCleaner = new RuntimeDistCleaner();

    public TestEnvironment(TestModuleLayerManager moduleLayerManager) {
        this.moduleLayerManager = moduleLayerManager;
    }

    @Override
    public <T> Optional<T> getProperty(TypesafeMap.Key<T> key) {
        return map.get(key);
    }

    @Override
    public <T> T computePropertyIfAbsent(TypesafeMap.Key<T> key, Function<? super TypesafeMap.Key<T>, ? extends T> valueFunction) {
        return map.computeIfAbsent(key, valueFunction);
    }

    @Override
    public Optional<ILaunchPluginService> findLaunchPlugin(String name) {
        return switch (name) {
            case "accesstransformer" -> Optional.ofNullable(accessTransformerService);
            case "runtimedistcleaner" -> Optional.ofNullable(runtimeDistCleaner);
            default -> throw new IllegalStateException("Unexpected value: " + name);
        };
    }

    @Override
    public Optional<ILaunchHandlerService> findLaunchHandler(String name) {
        return ServiceLoader.load(ILaunchHandlerService.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(service -> service.name().equals(name))
                .findFirst();
    }

    @Override
    public Optional<IModuleLayerManager> findModuleLayerManager() {
        return Optional.of(moduleLayerManager);
    }

    public Stream<ILaunchPluginService> getLaunchPlugins() {
        return Stream.of(
                accessTransformerService,
                runtimeDistCleaner).filter(Objects::nonNull);
    }
}
