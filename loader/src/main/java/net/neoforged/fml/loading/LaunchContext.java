/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.niofs.union.UnionFileSystem;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforgespi.ILaunchContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LaunchContext implements ILaunchContext {
    private static final Logger LOG = LoggerFactory.getLogger(LaunchContext.class);
    private final IEnvironment environment;
    private final Path gameDirectory;
    private final List<String> modLists;
    private final List<String> mods;
    private final List<String> mavenRoots;
    private final Set<Path> locatedPaths = new HashSet<>();
    private final Dist requiredDistribution;
    private final List<File> unclaimedClassPathEntries;

    LaunchContext(
            IEnvironment environment,
            Dist requiredDistribution,
            Path gameDirectory,
            List<String> modLists,
            List<String> mods,
            List<String> mavenRoots,
            List<File> unclaimedClassPathEntries) {
        this.environment = environment;
        this.gameDirectory = gameDirectory;
        this.modLists = modLists;
        this.mods = mods;
        this.mavenRoots = mavenRoots;
        this.requiredDistribution = requiredDistribution;
        this.unclaimedClassPathEntries = unclaimedClassPathEntries;
        LOG.debug(LogMarkers.SCAN, "Located paths when launch context was created: {}", locatedPaths);
    }

    @Override
    public Dist getRequiredDistribution() {
        return requiredDistribution;
    }

    @Override
    public Path gameDirectory() {
        return gameDirectory;
    }

    private Path unpackPath(Path path) {
        if (path.getFileSystem() instanceof UnionFileSystem unionFileSystem) {
            return unionFileSystem.getPrimaryPath();
        }
        return path;
    }

    @Override
    public <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass) {
        return ServiceLoader.load(serviceClass).stream();
    }

    @Override
    public boolean isLocated(Path path) {
        return locatedPaths.contains(unpackPath(path));
    }

    public boolean addLocated(Path path) {
        return locatedPaths.add(unpackPath(path));
    }

    @Override
    public List<File> getUnclaimedClassPathEntries() {
        return unclaimedClassPathEntries.stream()
                .filter(p -> !isLocated(p.toPath()))
                .toList();
    }

    @Override
    public IEnvironment environment() {
        return environment;
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
