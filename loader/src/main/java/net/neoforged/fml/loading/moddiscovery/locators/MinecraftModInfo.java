/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.electronwill.nightconfig.core.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

        String minecraftVersion;
        try (var in = Files.newBufferedReader(modFile.findResource("version.json"), StandardCharsets.UTF_8)) {
            var versionElement = new Gson().fromJson(in, JsonObject.class);
            var idPrimitive = versionElement.getAsJsonPrimitive("id");
            if (idPrimitive == null) {
                throw new IllegalArgumentException("Minecraft version.json found in " + modFile.getFilePath() + " is missing 'id' field. Available fields are: " + versionElement.keySet());
            }
            minecraftVersion = idPrimitive.getAsString();
        } catch (IOException e) {
            minecraftVersion = FMLLoader.versionInfo().mcVersion();
            if (minecraftVersion == null) {
                throw new IllegalArgumentException("Could not find version.json in Minecraft mod jar: " + modFile.getFilePath());
            }
        }

        // We haven't changed this in years, and I can't be asked right now to special case this one file in the path.
        final var conf = Config.inMemory();
        conf.set("modLoader", "minecraft");
        conf.set("loaderVersion", "1");
        conf.set("license", "Mojang Studios, All Rights Reserved");
        final var mods = Config.inMemory();
        mods.set("modId", "minecraft");
        mods.set("version", minecraftVersion);
        mods.set("displayName", "Minecraft");
        mods.set("description", "Minecraft");
        conf.set("mods", List.of(mods));

        final NightConfigWrapper configWrapper = new NightConfigWrapper(conf);
        return new ModFileInfo(modFile, configWrapper, configWrapper::setFile, List.of());
    }
}
