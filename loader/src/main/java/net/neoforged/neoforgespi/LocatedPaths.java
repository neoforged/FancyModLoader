/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import java.nio.file.Path;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface LocatedPaths {
    /**
     * Checks if a given path was already found by a previous locator, or may be already loaded.
     */
    boolean isLocated(Path path);

    /**
     * Marks a path as being located and returns true if it was not previously located.
     */
    boolean addLocated(Path path);
}
