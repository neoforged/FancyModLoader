/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

/**
 * Used to identify the source of a {@link IModFile}.
 */
public interface IModFileSource {
    /**
     * The name of the provider.
     * Has to be unique between all providers loaded into the runtime.
     *
     * @return The name.
     */
    String name();
}
