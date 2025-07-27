/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class FMLMixinTransformationService implements ITransformationService {
    @Override
    public String name() {
        return FMLMixinLaunchPlugin.NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        var plugin = (FMLMixinLaunchPlugin) environment.findLaunchPlugin(FMLMixinLaunchPlugin.NAME).orElseThrow(() -> new IllegalStateException("FMLMixinLaunchPlugin not found!"));

        return List.of(new Resource(IModuleLayerManager.Layer.SERVICE, List.of(plugin.createGeneratedCodeContainer())));
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
