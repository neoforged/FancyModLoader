/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.electronwill.nightconfig.core.Config;
import java.util.List;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;

final class MinecraftModInfo {
    private MinecraftModInfo() {}

    public static IModFileInfo buildMinecraftModInfo(final IModFile iModFile) {
        final ModFile modFile = (ModFile) iModFile;

        // We haven't changed this in years, and I can't be asked right now to special case this one file in the path.
        final var conf = Config.inMemory();
        conf.set("modLoader", "minecraft");
        conf.set("loaderVersion", "1");
        conf.set("license", "All Rights Reserved");
        final var mods = Config.inMemory();
        mods.set("modId", "minecraft");
        mods.set("version", FMLLoader.versionInfo().mcVersion());
        mods.set("displayName", "Minecraft");
        mods.set("authors", "Mojang Studios");
        mods.set("description", "");
        conf.set("mods", List.of(mods));

        final NightConfigWrapper configWrapper = new NightConfigWrapper(conf);
        return new ModFileInfo(modFile, configWrapper, configWrapper::setFile, List.of());
    }
}
