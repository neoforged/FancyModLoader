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
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ClasspathResourceUtils {
    private ClasspathResourceUtils() {}

    public static List<Path> findFileSystemRootsOfFileOnClasspath(String relativePath) {
        // If we're loaded through a module, it means the original classpath is inaccessible through the context CL
        var classLoader = Thread.currentThread().getContextClassLoader();
        if (ClasspathResourceUtils.class.getModule().isNamed()) {
            classLoader = ClassLoader.getSystemClassLoader();
        }

        return findFileSystemRootsOfFileOnClasspath(classLoader, relativePath);
    }

    public static Path getRootFromResourceUrl(String relativePath, URL resourceUrl) {
        if ("jar".equals(resourceUrl.getProtocol())) {
            var fileUri = URI.create(resourceUrl.toString().split("!")[0].substring("jar:".length()));
            return Paths.get(fileUri);
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

            return resourcePath;
        }
    }

    @Nullable
    public static Path findFileSystemRootOfFileOnClasspath(ClassLoader classLoader, String relativePath) {
        var resource = classLoader.getResource(relativePath);
        if (resource == null) {
            return null;
        }

        return getRootFromResourceUrl(relativePath, resource);
    }

    public static List<Path> findFileSystemRootsOfFileOnClasspath(ClassLoader classLoader, String relativePath) {
        // Find the directory that contains the Minecraft classes via the system classpath
        Iterator<URL> resourceIt;
        try {
            resourceIt = classLoader.getResources(relativePath).asIterator();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to enumerate classpath locations of " + relativePath);
        }

        var result = new LinkedHashSet<Path>();
        while (resourceIt.hasNext()) {
            var resourceUrl = resourceIt.next();
            result.add(getRootFromResourceUrl(relativePath, resourceUrl));
        }

        return new ArrayList<>(result);
    }

    public static Path findFileSystemRootOfFileOnClasspath(String relativePath) {
        var paths = findFileSystemRootsOfFileOnClasspath(relativePath);

        if (paths.isEmpty()) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.failed_to_find_on_classpath", relativePath));
        } else if (paths.size() > 1) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.multiple_copies_on_classpath", relativePath, paths));
        }

        return paths.getFirst();
    }
}
