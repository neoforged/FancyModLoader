/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import java.nio.file.Files;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Locates the Minecraft and NeoForge jars in a production environment from the libraries folder.
 */
public class ProductionProvider implements IModFileCandidateLocator {
    private final MavenCoordinate minecraftArtifact;
    private final MavenCoordinate neoforgeArtifact;

    public ProductionProvider(MavenCoordinate minecraftArtifact, MavenCoordinate neoforgeArtifact) {
        this.minecraftArtifact = minecraftArtifact;
        this.neoforgeArtifact = neoforgeArtifact;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        var minecraftJar = LibraryFinder.findPathForMaven(minecraftArtifact);
        if (!Files.exists(minecraftJar)) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.missing_minecraft_jar").withAffectedPath(minecraftJar));
        }
        if (pipeline.addPath(minecraftJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE).isEmpty()) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_minecraft_jar").withAffectedPath(minecraftJar));
        }

        var neoforgeJar = LibraryFinder.findPathForMaven(neoforgeArtifact);
        if (!Files.exists(neoforgeJar)) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.missing_neoforge_jar").withAffectedPath(neoforgeJar));
        }
        if (pipeline.addPath(neoforgeJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE).isEmpty()) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar").withAffectedPath(neoforgeJar));
        }
    }

    @Override
    public String toString() {
        return "production locator";
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }
}
