/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import java.nio.file.Files;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * Any file that remains will just be added to the library set.
 */
public class ClasspathLibrariesLocator implements IModFileCandidateLocator {
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // When the application class-loader is reachable, do not run
        // TODO var loader = getClass().getClassLoader();
        // TODO do {
        // TODO     if (loader == ClassLoader.getSystemClassLoader()) {
        // TODO         return;
        // TODO     }
        // TODO     loader = loader.getParent();
        // TODO } while (loader != null);

        for (var classPathItem : context.getUnclaimedClassPathEntries()) {
            var path = classPathItem.toPath();
            if (Files.isRegularFile(path)) {
                pipeline.addLibrary(path);
            }
        }
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY;
    }

    @Override
    public String toString() {
        return "classpath libraries locator";
    }
}
