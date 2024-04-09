/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import java.util.List;
import java.util.ServiceLoader;
import net.neoforged.neoforgespi.ILaunchContext;

record LaunchContext(
        IEnvironment environment,
        IModuleLayerManager moduleLayerManager,
        List<String> modLists,
        List<String> mods,
        List<String> mavenRoots) implements ILaunchContext {
    @Override
    public <T> ServiceLoader<T> createServiceLoader(Class<T> serviceClass) {
        var moduleLayer = moduleLayerManager.getLayer(IModuleLayerManager.Layer.SERVICE).orElseThrow();
        return ServiceLoader.load(moduleLayer, serviceClass);
    }

    @Override
    public void reportWarning() {
        throw new UnsupportedOperationException();
    }
}
