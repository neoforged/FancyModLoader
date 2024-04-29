/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import cpw.mods.jarhandling.JarContents;
import org.jetbrains.annotations.Nullable;

/**
 * Inspects {@link JarContents} found by {@link IModFileCandidateLocator} and tries to turn them into {@link IModFile}.
 * <p>
 * Picked up via ServiceLoader.
 */
public interface IModFileReader extends IOrderedProvider {
    /**
     * Provides a mod from the given {@code jar}.
     * Any thrown exception will be reported in relationship to the given jar contents.
     *
     * @param jar        the mod jar contents
     * @param attributes The attributes relating to this mod files discovery.
     * @return {@code null} if this provider can't handle the given jar,
     *         otherwise the mod-file created from the given contents and attributes.
     */
    @Nullable
    IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes);
}
