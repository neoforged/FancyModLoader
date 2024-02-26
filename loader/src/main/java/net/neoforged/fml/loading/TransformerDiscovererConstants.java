/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;

import java.util.Set;

/**
 * Defines a class containing constants which implementations of {@link ITransformerDiscoveryService}
 * may use.
 */
public class TransformerDiscovererConstants {
    private TransformerDiscovererConstants() { }

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
        "net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider"
    );
}
