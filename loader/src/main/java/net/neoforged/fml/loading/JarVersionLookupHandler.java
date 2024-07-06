/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarInputStream;

/**
 * Finds Version data from a package, with possible default values
 */
public class JarVersionLookupHandler {
    public static Optional<String> getVersion(final Class<?> clazz) {
        if (clazz.getModule() != null && clazz.getModule().getName() != null) {
            if (clazz.getModule().getDescriptor() != null) {
                var version = clazz.getModule().getDescriptor().rawVersion();
                if (version.isPresent()) {
                    return version;
                }
            }

            // the version.properties file was written by a Gradle task in the project
            try (var in = clazz.getModule().getResourceAsStream("version.properties")) {
                if (in == null) {
                    return Optional.empty();
                }

                var properties = new Properties();
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                return Optional.ofNullable(properties.getProperty("projectVersion"));
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        // When loaded through a non-modular class-loader, we do get the implementation version from the manifest
        if (clazz.getPackage() != null && clazz.getPackage().getImplementationVersion() != null) {
            return Optional.of(clazz.getPackage().getImplementationVersion());
        }

        return Optional.empty();
    }
}
