/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.TypesafeMap;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;
import net.neoforged.fml.common.asm.AccessTransformerService;
import net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender;
import net.neoforged.fml.loading.mixin.FMLMixinClassProcessor;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevDistCleaner;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.Nullable;

public class TestEnvironment implements IEnvironment {
    private final TypesafeMap map = new TypesafeMap(IEnvironment.class);
    private final TestModuleLayerManager moduleLayerManager;
    @Nullable
    public AccessTransformerService accessTransformerService = new AccessTransformerService();
    @Nullable
    public RuntimeEnumExtender runtimeEnumExtender = new RuntimeEnumExtender();
    @Nullable
    public NeoForgeDevDistCleaner neoForgeDevDistCleaner = new NeoForgeDevDistCleaner();
    @Nullable
    public FMLMixinClassProcessor fmlMixinClassProcessor = new FMLMixinClassProcessor();
    @Nullable
    public CoreModsTransformerProvider coreModsTransformerProvider = new CoreModsTransformerProvider();

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
    public Optional<ClassProcessor> findClassProcessor(ProcessorName name) {
        return getClassProcessors().filter(cp -> cp.name().equals(name)).findFirst();
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

    public Stream<ClassProcessor> getClassProcessors() {
        return Stream.of(accessTransformerService,
                runtimeEnumExtender,
                neoForgeDevDistCleaner,
                fmlMixinClassProcessor).filter(Objects::nonNull);
    }

    public Stream<ClassProcessorProvider> getClassProcessorProviders() {
        return Stream.<ClassProcessorProvider>of(coreModsTransformerProvider).filter(Objects::nonNull);
    }
}
