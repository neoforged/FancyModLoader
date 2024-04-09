/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * Defines a class containing constants which implementations of {@link ITransformerDiscoveryService}
 * may use.
 */
public final class TransformerDiscovererConstants {
    private TransformerDiscovererConstants() {}

    /**
     * Defines the set of FML service types which should be loaded on the
     * {@link Layer#SERVICE} module layer.
     */
    public static final Set<String> SERVICES = Set.of(
            "cpw.mods.modlauncher.api.ITransformationService",
            "net.neoforged.neoforgespi.locating.IModLocator",
            "net.neoforged.neoforgespi.locating.IDependencyLocator",
            "net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
            "net.neoforged.fml.loading.ImmediateWindowProvider", // FIXME: remove this when removing the legacy ImmediateWindowProvider
            "net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider");

    public static boolean shouldLoadInServiceLayer(Collection<Path> paths) {
        return shouldLoadInServiceLayer(JarContents.of(paths));
    }

    public static boolean shouldLoadInServiceLayer(Path path) {
        return shouldLoadInServiceLayer(JarContents.of(path));
    }

    public static boolean shouldLoadInServiceLayer(JarContents jarContents) {
        JarMetadata metadata = JarMetadata.from(jarContents);
        return metadata.providers().stream()
                .map(SecureJar.Provider::serviceName)
                .anyMatch(SERVICES::contains);
    }
}
