/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import net.neoforged.fml.loading.JarVersionLookupHandler;

public final class FMLVersion {
    private FMLVersion() {}

    public static String getVersion() {
        return JarVersionLookupHandler.getVersion(FMLVersion.class.getClassLoader(), "net.neoforged.fancymodloader", "loader");
    }
}
