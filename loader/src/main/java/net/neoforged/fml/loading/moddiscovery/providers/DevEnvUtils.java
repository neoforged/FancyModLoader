/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DevEnvUtils {
    private DevEnvUtils() {}

    static Path findFileSystemRootOfFileOnClasspath(String relativePath) {
        // Find the directory that contains the Minecraft classes via the system classpath

        var resourceUrls = new ArrayList<URL>();
        try {
            Thread.currentThread().getContextClassLoader().getResources(relativePath)
                    .asIterator().forEachRemaining(resourceUrls::add);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to enumerate classpath locations of " + relativePath);
        }

        if (resourceUrls.isEmpty()) {
            throw new IllegalArgumentException("Resource not found on classpath: " + relativePath); // TODO
        } else if (resourceUrls.size() > 1) {
            throw new IllegalArgumentException("Classpath contains multiple copies of " + relativePath + ": " + resourceUrls); // TODO
        }

        var resourceUrl = resourceUrls.getFirst();
        Path result;
        try {
            if ("jar".equals(resourceUrl.getProtocol())) {
                var fileUri = URI.create(resourceUrl.toString().split("!")[0].substring("jar:".length()));
                return Paths.get(fileUri);
            } else {
                result = Paths.get(resourceUrl.toURI());
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Failed to convert " + resourceUrl + " to URI");
        }

        // Walk back from the resource path up to the content root that contained it
        var current = Paths.get(relativePath);
        while (current != null) {
            current = current.getParent();
            result = result.getParent();
            if (result == null) {
                throw new IllegalArgumentException("Resource " + resourceUrl + " did not have same nesting depth as " + relativePath);
            }
        }

        return result;
    }
}
