/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import net.neoforged.api.distmarker.Dist;

public class FMLEnvironment {
    public static final Dist dist = FMLLoader.getDist();
    public static final boolean production = FMLLoader.isProduction() || System.getProperties().containsKey("production");
}
