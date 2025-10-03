/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.classloading;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarResource;
import org.jetbrains.annotations.ApiStatus;

/**
 * Utilities for creating {@link java.lang.module.ModuleDescriptor} from {@link JarContents}.
 * <p>
 * Most of this code also lives in the JDK in the internal ModulePath class.
 */
@ApiStatus.Internal
public final class ModuleDescriptorFactory {
    private ModuleDescriptorFactory() {}

    /**
     * Scans an automatic module for the following and applies them to the given module builder.
     *
     * <ul>
     * <li>Packages containing class files.</li>
     * <li>Java {@link java.util.ServiceLoader} providers found in {@code META-INF/services/}</li>
     * </ul>
     *
     * @param excludedRootDirectories Allows for additional root directories to be completely ignored for scanning
     *                                packages. Useful if it is known beforehand that certain subdirectories
     *                                are unlikely to contain classes.
     */
    public static void scanAutomaticModule(
            JarContents jar,
            ModuleDescriptor.Builder builder,
            String... excludedRootDirectories) {
        Set<String> ignoredRootDirs = Set.of(excludedRootDirectories);

        Set<String> packageNames = new HashSet<>();
        Map<String, JarResource> serviceProviderFiles = new HashMap<>();
        jar.visitContent((relativePath, resource) -> {
            // Ignore all content in META-INF except for service files
            if (relativePath.startsWith("META-INF/services/")) {
                String filename = relativePath.substring(relativePath.lastIndexOf('/') + 1);

                // Ignore files in META-INF/services/ whose filenames are not valid Java class names
                if (JlsConstants.isTypeName(filename)) {
                    serviceProviderFiles.put(filename, resource.retain());
                }
            } else {
                // In automatic modules, only packages with .class files are considered,
                // unlike with normal modules where resources would also be scanned.
                if (relativePath.endsWith(".class")) {
                    var lastSeparator = relativePath.lastIndexOf('/');
                    if (lastSeparator > 0) {
                        String relativeDir = relativePath.substring(0, lastSeparator);
                        var packageName = relativeDir.replace('/', '.');
                        if (JlsConstants.isTypeName(packageName)) {
                            packageNames.add(packageName);
                        }
                    }
                }
            }
        });
        builder.packages(packageNames);

        for (var serviceProviderEntry : serviceProviderFiles.entrySet()) {
            var serviceProviderName = serviceProviderEntry.getKey();

            try (var reader = serviceProviderEntry.getValue().bufferedReader(StandardCharsets.UTF_8)) {
                parseServiceFile(serviceProviderName, reader, packageNames, builder);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to parse service provider file " + serviceProviderName + " in " + jar, e);
            }
        }
    }

    /**
     * Parses a Java ServiceLoader file and adds its content as a provided service to the given module
     * descriptor builder.
     * <p>Equivalent to the code found in ModulePath#deriveModuleDescriptor(JarFile)
     */
    private static void parseServiceFile(String serviceName, BufferedReader reader, Set<String> packageNames, ModuleDescriptor.Builder builder) throws IOException {
        List<String> providerClasses = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            // Strip comments
            var startOfComment = line.indexOf('#');
            if (startOfComment != -1) {
                line = line.substring(0, startOfComment);
            }
            line = line.trim(); // Trim whitespace *after* removing the comment

            // We're parsing service files after scanning for packages,
            // which means we can validate at this point that a service provider
            // only provides a class that is contained in the Jar file.
            if (!line.isEmpty()) {
                String packageName = JlsConstants.getPackageName(line);
                if (!packageNames.contains(packageName)) {
                    String msg = "Service provider file " + serviceName + " contains service that is not in this Jar file: " + line;
                    throw new InvalidModuleDescriptorException(msg);
                }
                providerClasses.add(line);
            }
        }

        if (!providerClasses.isEmpty()) {
            builder.provides(serviceName, providerClasses);
        }
    }

    /**
     * Scans a given Jar for all packages that contain files for use with {@link ModuleDescriptor#read}.
     * <p>Unlike {@link #scanAutomaticModule}, this also finds packages that contain only resource files, which is
     * consistent with the behavior of {@link java.lang.module.ModuleFinder} for modular Jar files.
     */
    public static Set<String> scanModulePackages(JarContents jar) {
        Set<String> packageNames = new HashSet<>();

        jar.visitContent((relativePath, resource) -> {
            var lastSeparator = relativePath.lastIndexOf('/');
            if (lastSeparator < 1) {
                return; // File is in the default package
            }

            var packageName = relativePath.substring(0, lastSeparator).replace('/', '.');
            if (JlsConstants.isTypeName(packageName)) {
                packageNames.add(packageName);
            }
        });

        return Set.copyOf(packageNames);
    }
}
