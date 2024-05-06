/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.lowcodemod;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.JarVersionLookupHandler;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

public class LowCodeModLanguageProvider implements IModLanguageLoader {
    @Override
    public String name() {
        return "lowcodefml";
    }

    @Override
    public ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer) {
        return new LowCodeModContainer(info, modFileScanResults, layer);
    }

    @Override
    public String version() {
        final Path lpPath;
        try {
            lpPath = Paths.get(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Huh?", e);
        }
        return JarVersionLookupHandler.getVersion(this.getClass()).orElse(Files.isDirectory(lpPath) ? FMLLoader.versionInfo().fmlVersion() : null);
    }
}
