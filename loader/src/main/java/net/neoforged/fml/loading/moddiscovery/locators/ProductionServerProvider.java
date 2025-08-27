/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LibraryFinder;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * Locates the Minecraft server files in a production environment.
 */
public class ProductionServerProvider implements IModFileCandidateLocator {
    private final List<MavenCoordinate> additionalContent;

    public ProductionServerProvider(List<MavenCoordinate> additionalContent) {
        this.additionalContent = additionalContent;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        var vers = FMLLoader.versionInfo();

        try {
            var mc = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "srg", vers.mcAndNeoFormVersion());
            if (!Files.exists(mc)) {
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withAffectedPath(mc));
                return;
            }
            var mcextra = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "extra", vers.mcAndNeoFormVersion());
            if (!Files.exists(mcextra)) {
                pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withAffectedPath(mc));
                return;
            }

            var content = new ArrayList<JarContents.FilteredPath>();
            content.add(new JarContents.FilteredPath(mc));
            // We only want it for its resources. So filter everything else out.
            content.add(new JarContents.FilteredPath(mcextra, path -> {
                return !path.endsWith(".class") && !path.startsWith("META-INF/");
            }));
            for (var artifact : additionalContent) {
                var extraPath = LibraryFinder.findPathForMaven(artifact);
                if (!Files.exists(extraPath)) {
                    pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withAffectedPath(extraPath));
                    return;
                }
                content.add(new JarContents.FilteredPath(extraPath));
            }

            var mcJarContents = JarContents.ofFilteredPaths(content);

            var mcJarMetadata = new ModJarMetadata(mcJarContents);
            var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
            var mcjar = IModFile.create(mcSecureJar, MinecraftModInfo::buildMinecraftModInfo);
            mcJarMetadata.setModFile(mcjar);

            pipeline.addModFile(mcjar);
        } catch (Exception e) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.corrupted_installation").withCause(e));
        }
    }

    @Override
    public String toString() {
        var result = new StringBuilder("production server provider");
        for (var mavenCoordinate : additionalContent) {
            result.append(" +").append(mavenCoordinate);
        }
        return result.toString();
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }
}
