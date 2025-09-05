/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.nio.file.Files;

/**
 * This locator finds mods and game libraries that are passed as jar files on the classpath.
 */
public class InDevJarLocator implements IModFileCandidateLocator {
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // Search for mods first, since all mods need to be located
        for (var path : ClasspathResourceUtils.findFileSystemRootsOfFileOnClasspath(JarModsDotTomlModFileReader.MODS_TOML)) {
            if (Files.isRegularFile(path)) {
                pipeline.addPath(path, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY);
            }
        }

        // For game libraries, we need to inspect the manifests themselves for the mod type attribute
    }

    @Override
    public int getPriority() {
        // We need to get the explicitly grouped items out of the way before anyone else claims them
        return HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public String toString() {
        return "indevjar";
    }
}
