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
 * Any jar file that remains on the classpath will just be added to the library set, if they are not
 * already accesible via class loader delegation.
 * Jars that have explicit markings as FML mods or libs are handled by {@link InDevJarLocator}.
 */
public class ClasspathLibrariesLocator implements IModFileCandidateLocator {
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // When the application class-loader is reachable, do not run, since libraries are already accessible then
        var loader = getClass().getClassLoader();
        do {
            if (loader == ClassLoader.getSystemClassLoader()) {
                return;
            }
            loader = loader.getParent();
        } while (loader != null);

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
