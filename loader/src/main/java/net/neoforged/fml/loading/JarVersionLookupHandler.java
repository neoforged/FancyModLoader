/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Finds Version data from a package, with possible default values
 */
public class JarVersionLookupHandler {
    public static Optional<String> getVersion(final Class<?> clazz) {
        if (clazz.getModule() != null && clazz.getModule().getName() != null) {
            // Named modules can be versioned directly via their jar file.
            if (clazz.getModule().getDescriptor() != null) {
                var version = clazz.getModule().getDescriptor().rawVersion();
                if (version.isPresent()) {
                    return version;
                }
            }
        }

        // When loaded through a non-modular class-loader, we do get the implementation version from the manifest
        if (clazz.getPackage() != null && clazz.getPackage().getImplementationVersion() != null) {
            return Optional.of(clazz.getPackage().getImplementationVersion());
        }

        return Optional.empty();
    }

    public static String getVersion(ClassLoader loader, String group, String artifact) {
        // the version.properties file was written by a Gradle task in the project
        String versionFile = "META-INF/versions/" + group + "." + artifact;
        try (var in = loader.getResourceAsStream(versionFile)) {
            if (in == null) {
                throw new IllegalStateException("Failed to find version marker file " + versionFile);
            }

            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            var version = properties.getProperty("projectVersion");
            if (version == null) {
                throw new IllegalStateException("Version marker file " + versionFile + " was found, but did not have a projectVersion property");
            }
            return version;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read version marker file " + versionFile, e);
        }
    }
}
