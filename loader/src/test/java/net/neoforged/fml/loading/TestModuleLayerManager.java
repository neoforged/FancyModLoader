/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IModuleLayerManager;
import java.lang.module.Configuration;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

public class TestModuleLayerManager implements IModuleLayerManager {
    public static final ModuleLayer SERVICE_LAYER = createEmptyLayer();
    public static final ModuleLayer PLUGIN_LAYER = createEmptyLayer();

    private final EnumMap<Layer, ModuleLayer> layers = new EnumMap<>(Layer.class);

    public void setLayer(Layer layer, ModuleLayer moduleLayer) {
        this.layers.put(layer, moduleLayer);
    }

    @Override
    public Optional<ModuleLayer> getLayer(Layer layer) {
        return Optional.ofNullable(layers.get(layer));
    }

    private static ModuleLayer createEmptyLayer() {
        return ModuleLayer.defineModulesWithOneLoader(
                Configuration.empty(),
                List.of(),
                ClassLoader.getSystemClassLoader()).layer();
    }
}
