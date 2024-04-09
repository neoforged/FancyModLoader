/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import java.util.List;
import java.util.ServiceLoader;
import net.neoforged.neoforgespi.ILaunchContext;

class TestLaunchContext implements ILaunchContext {
    private IEnvironment environment;

    public TestLaunchContext(IEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public IEnvironment environment() {
        return environment;
    }

    @Override
    public <T> ServiceLoader<T> createServiceLoader(Class<T> serviceClass) {
        return ServiceLoader.load(serviceClass);
    }

    @Override
    public void reportWarning() {}

    @Override
    public List<String> modLists() {
        return List.of();
    }

    @Override
    public List<String> mods() {
        return List.of();
    }

    @Override
    public List<String> mavenRoots() {
        return List.of();
    }
}
