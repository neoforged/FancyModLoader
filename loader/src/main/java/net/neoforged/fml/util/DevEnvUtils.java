/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class DevEnvUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DevEnvUtils.class);

    private DevEnvUtils() {}

    public static List<Path> findFileSystemRootsOfFileOnClasspath(String relativePath) {
        // If we're loaded through a module, it means the original classpath is inaccessible through the context CL
        var classLoader = Thread.currentThread().getContextClassLoader();
        if (DevEnvUtils.class.getModule().isNamed()) {
            classLoader = ClassLoader.getSystemClassLoader();
        }

        // Find the directory that contains the Minecraft classes via the system classpath
        Iterator<URL> resourceIt;
        try {
            resourceIt = classLoader.getResources(relativePath).asIterator();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to enumerate classpath locations of " + relativePath);
        }

        // Remove duplicates, but maintain order!
        Set<Path> result = new LinkedHashSet<>();
        while (resourceIt.hasNext()) {
            var resourceUrl = resourceIt.next();

            if ("jar".equals(resourceUrl.getProtocol())) {
                var fileUri = URI.create(resourceUrl.toString().split("!")[0].substring("jar:".length()));
                result.add(Paths.get(fileUri));
            } else {
                Path resourcePath;
                try {
                    resourcePath = Paths.get(resourceUrl.toURI());
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Failed to convert " + resourceUrl + " to URI");
                }

                // Walk back from the resource path up to the content root that contained it
                var current = Paths.get(relativePath);
                while (current != null) {
                    current = current.getParent();
                    resourcePath = resourcePath.getParent();
                    if (resourcePath == null) {
                        throw new IllegalArgumentException("Resource " + resourceUrl + " did not have same nesting depth as " + relativePath);
                    }
                }

                result.add(resourcePath);
            }
        }

        return new ArrayList<>(result);
    }

    public static Path findFileSystemRootOfFileOnClasspath(String relativePath) {
        var paths = findFileSystemRootsOfFileOnClasspath(relativePath);

        if (paths.isEmpty()) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.failed_to_find_on_classpath", relativePath));
        } else if (paths.size() > 1) {
            // Warn for anything past the first. It's impossible to know if the user actually intended this setup,
            // but to allow launching in uncommon configurations, we'll allow it.
            for (int i = 1; i < paths.size(); i++) {
                var path = paths.get(i);
                LOG.warn("Found multiple copies of {} on classpath. Ignoring copy from {}, using {} instead.", relativePath, path, paths.getFirst());
            }
        }

        return paths.getFirst();
    }
}
