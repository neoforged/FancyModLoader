/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import cpw.mods.jarhandling.JarContents;
import org.jetbrains.annotations.Nullable;

/**
 * This support interface wraps all available {@linkplain IModFileReader mod providers} for use by
 * {@link IDependencyLocator dependency locators}.
 */
@FunctionalInterface
public interface IModFileReaderFacade {
    @Nullable
    LoadResult<IModFile> readModFile(JarContents jarContents, @Nullable IModFile parent);
}
