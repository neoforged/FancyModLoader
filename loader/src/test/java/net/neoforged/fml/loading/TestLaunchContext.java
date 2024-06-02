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
import java.util.stream.Stream;
import net.neoforged.neoforgespi.ILaunchContext;

class TestLaunchContext implements ILaunchContext {
    private final ILaunchContext launchContext;
    private final Set<Path> locatedPaths;

    public TestLaunchContext(ILaunchContext launchContext, Set<Path> locatedPaths) {
        this.launchContext = launchContext;
        this.locatedPaths = new HashSet<>(locatedPaths);
    }

    @Override
    public IEnvironment environment() {
        return launchContext.environment();
    }

    @Override
    public <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass) {
        return Stream.concat(
                launchContext.loadServices(serviceClass),
                ServiceLoader.load(serviceClass).stream()).distinct();
    }

    @Override
    public List<String> modLists() {
        return launchContext.modLists();
    }

    @Override
    public List<String> mods() {
        return launchContext.mods();
    }

    @Override
    public List<String> mavenRoots() {
        return launchContext.mavenRoots();
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
