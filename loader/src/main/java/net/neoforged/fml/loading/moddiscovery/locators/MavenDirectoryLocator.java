/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.moddiscovery.ModListHandler;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Locates mod-file candidates from Maven repository folder-structures.
 * Maven coordinates for mods must be provided via the FML command line.
 */
public class MavenDirectoryLocator implements IModFileCandidateLocator {
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        var mavenRoots = context.mavenRoots();
        var mavenRootPaths = mavenRoots.stream().map(n -> FMLPaths.GAMEDIR.get().resolve(n)).collect(Collectors.toList());
        var mods = context.mods();
        var listedMods = ModListHandler.processModLists(context.modLists(), mavenRootPaths);

        // find the modCoords path in each supplied maven path, and turn it into a mod file
        var modCoordinates = Stream.concat(mods.stream(), listedMods.stream()).toList();
        for (var modCoordinate : modCoordinates) {
            Path relativePath;
            try {
                relativePath = MavenCoordinate.parse(modCoordinate).toRelativeRepositoryPath();
            } catch (Exception e) {
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.invalid_maven_coordinate", modCoordinate).withCause(e));
                continue;
            }

            var path = mavenRootPaths.stream().map(root -> root.resolve(relativePath)).filter(Files::exists).findFirst();
            if (path.isPresent()) {
                pipeline.addPath(path.get(), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
            } else {
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.maven_coordinate_not_found", modCoordinate, mavenRootPaths));
            }
        }
    }

    @Override
    public String toString() {
        return "maven libs";
    }
}
