/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.fml.loading.FMLLoader;

/**
 * Run when FML is bootstrapped in a unit testing context.
 */
public interface JUnitGameBootstrapper {
    void bootstrap(FMLLoader loader);
}
