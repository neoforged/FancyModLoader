/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.mclanguageprovider;

import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.ModFileScanData;

public class MinecraftModLanguageProvider implements IModLanguageProvider {
    @Override
    public String name() {
        return "minecraft";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return (sd) -> sd.addLanguageLoader(Map.of("minecraft", new MinecraftModTarget()));
    }

    public static class MinecraftModTarget implements IModLanguageLoader {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, final ModuleLayer gameLayer) {
            return (T) new MinecraftModContainer(info);
        }
    }
}
