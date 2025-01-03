/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;

/**
 * Global environment variables - allows discoverability with other systems without full forge
 * dependency
 */
@Deprecated(forRemoval = true)
public class Environment {
    private static final Environment INSTANCE = new Environment();

    public static Environment get() {
        return INSTANCE;
    }

    public Dist getDist() {
        return FMLLoader.getDist();
    }
}
