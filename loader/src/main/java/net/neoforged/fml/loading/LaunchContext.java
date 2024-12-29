/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.niofs.union.UnionFileSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforgespi.ILaunchContext;

final class LaunchContext implements ILaunchContext {
    private final Path gameDirectory;
    private final List<String> modLists;
    private final List<String> mods;
    private final List<String> mavenRoots;
    private final Set<Path> locatedPaths = new HashSet<>();
    private final Dist requiredDistribution;
    private final List<File> unclaimedClassPathEntries;
    /**
     * Used to track where Jar files we extract to disk originally came from. Used for error reporting.
     */
    private final Map<Path, String> jarSourceInfo = new HashMap<>();

    LaunchContext(
            Dist requiredDistribution,
            Path gameDirectory,
            List<String> modLists,
            List<String> mods,
            List<String> mavenRoots,
            List<File> unclaimedClassPathEntries) {
        this.gameDirectory = gameDirectory;
        this.modLists = modLists;
        this.mods = mods;
        this.mavenRoots = mavenRoots;
        this.requiredDistribution = requiredDistribution;
        this.unclaimedClassPathEntries = unclaimedClassPathEntries;
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

    @Override
    public void setJarSourceDescription(Path path, String description) {
        jarSourceInfo.put(path, description);
    }

    @Override
    public String getJarSourceDescription(Path path) {
        return jarSourceInfo.get(path);
    }

    @Override
    public String relativizePath(Path path) {
        var gameDir = gameDirectory();

        String resultPath;

        if (gameDir != null && path.startsWith(gameDir)) {
            resultPath = gameDir.relativize(path).toString();
        } else if (Files.isDirectory(path)) {
            resultPath = path.toAbsolutePath().toString();
        } else {
            resultPath = path.getFileName().toString();
        }

        // Unify separators to ensure it is easier to test
        return resultPath.replace('\\', '/');
    }
}
