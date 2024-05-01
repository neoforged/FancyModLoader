/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import net.neoforged.neoforgespi.ILaunchContext;

class TestLaunchContext implements ILaunchContext {
    private final IEnvironment environment;
    private final Set<Path> locatedPaths;

    public TestLaunchContext(IEnvironment environment, Set<Path> locatedPaths) {
        this.environment = environment;
        this.locatedPaths = new HashSet<>(locatedPaths);
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

    @Override
    public boolean isLocated(Path path) {
        return locatedPaths.contains(path);
    }

    @Override
    public boolean addLocated(Path path) {
        return locatedPaths.add(path);
    }
}
