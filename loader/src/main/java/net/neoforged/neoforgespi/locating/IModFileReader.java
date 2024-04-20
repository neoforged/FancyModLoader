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
public non-sealed interface IModFileReader extends IModFileSource {
    /**
     * Provides a mod from the given {@code jar}.
     *
     * @param jar    the mod jar contents
     * @param parent The mod-file that is the logical parent of {@code jar}. This may be null if there is no parent.
     * @return {@code null} if this provider can't handle the given jar, otherwise a result indicating success or failure.
     */
    @Nullable
    LoadResult<IModFile> read(JarContents jar, @Nullable IModFile parent);
}
