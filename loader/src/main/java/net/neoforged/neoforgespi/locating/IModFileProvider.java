/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import java.util.List;
import net.neoforged.neoforgespi.ILaunchContext;

/**
 * Picked up via ServiceLoader
 */
public non-sealed interface IModFileProvider extends IModFileSource {
    /**
     * {@return a list of implicit mods that this provider can provide without being found by a locator}
     */
    List<LoadResult<IModFile>> provideModFiles(ILaunchContext launchContext);
}
