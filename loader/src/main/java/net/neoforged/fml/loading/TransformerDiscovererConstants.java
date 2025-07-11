/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IModFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a class containing constants which implementations of {@link ITransformerDiscoveryService}
 * may use.
 */
public final class TransformerDiscovererConstants {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerDiscovererConstants.class);

    private TransformerDiscovererConstants() {}

    /**
     * Defines the set of FML service types which should be loaded on the
     * {@link Layer#SERVICE} module layer.
     */
    public static final Set<String> SERVICES = Set.of(
            ITransformationService.class.getName(),
            IModFileCandidateLocator.class.getName(),
            IModFileReader.class.getName(),
            IDependencyLocator.class.getName(),
            GraphicsBootstrapper.class.getName(),
            net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider.class.getName());

    public static boolean shouldLoadInServiceLayer(Collection<Path> paths) {
        var contents = JarContents.of(paths);
        try {
            return shouldLoadInServiceLayer(contents);
        } finally {
            try {
                contents.close();
            } catch (IOException e) {
                LOGGER.error("Could not close JarContents {}", paths, e);
            }
        }
    }

    public static boolean shouldLoadInServiceLayer(Path path) {
        return shouldLoadInServiceLayer(List.of(path));
    }

    public static boolean shouldLoadInServiceLayer(JarContents jarContents) {
        // This tries to optimize for speed since this scan happens before any progress window is shown to the user
        // We try a module-info.class first, if it is present.
        try (var moduleInfoContent = jarContents.getResourceAsStream("module-info.class")) {
            if (moduleInfoContent != null) {
                // Module-info is present, read it without scanning for packages
                var moduleDescriptor = ModuleDescriptor.read(new BufferedInputStream(moduleInfoContent));
                return moduleDescriptor.provides().stream()
                        .map(ModuleDescriptor.Provides::service)
                        .anyMatch(SERVICES::contains);
            }
        } catch (InvalidModuleDescriptorException e) {
            throw new RuntimeException("Invalid module-info.class in " + jarContents, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read module-info.class from " + jarContents, e);
        }

        // If we get here, the Jar is non-modular, so we check for service files
        for (var service : SERVICES) {
            var serviceFile = "META-INF/services/" + service;
            try (var stream = jarContents.getResourceAsStream(serviceFile)) {
                if (stream != null) {
                    return true; // Found a match
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open service-file " + serviceFile + " in " + jarContents, e);
            }
        }

        return false; // No service file was found
    }
}
