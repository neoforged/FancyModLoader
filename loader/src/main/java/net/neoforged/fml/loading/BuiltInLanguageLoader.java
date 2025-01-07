/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import net.neoforged.fml.FMLVersion;
import net.neoforged.neoforgespi.language.IModLanguageLoader;

public abstract class BuiltInLanguageLoader implements IModLanguageLoader {
    @Override
    public String version() {
        return FMLVersion.getVersion();
    }
}
