/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import net.neoforged.neoforgespi.Environment;

import java.nio.file.Path;

/**
 * Functional interface for generating a custom {@link IModLocator} from a directory, with a specific name.
 *
 * FML provides this factory at {@link Environment.Keys#MODDIRECTORYFACTORY} during
 * locator construction.
 */
public interface IModDirectoryLocatorFactory {
    IModLocator build(Path directory, String name);
}
