/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.MavenCoordinateResolver;
import net.neoforged.fml.loading.moddiscovery.ModListHandler;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;

public class MavenDirectoryLocator implements IModFileCandidateLocator {
    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext context) {
        final List<String> mavenRoots = context.mavenRoots();
        final List<Path> mavenRootPaths = mavenRoots.stream().map(n -> FMLPaths.GAMEDIR.get().resolve(n)).collect(Collectors.toList());
        final List<String> mods = context.mods();
        final List<String> listedMods = ModListHandler.processModLists(context.modLists(), mavenRootPaths);

        List<Path> localModCoords = Stream.concat(mods.stream(), listedMods.stream()).map(MavenCoordinateResolver::get).toList();
        // find the modCoords path in each supplied maven path, and turn it into a mod file. (skips not found files)

        var modCoords = localModCoords.stream().map(mc -> mavenRootPaths.stream().map(root -> root.resolve(mc)).filter(Files::exists).findFirst().orElseThrow(() -> new IllegalArgumentException("Failed to locate requested mod coordinate " + mc))).collect(Collectors.toList());

        return modCoords.stream().map(IModFileCandidateLocator::result);
    }

    @Override
    public String name() {
        return "maven libs";
    }
}
