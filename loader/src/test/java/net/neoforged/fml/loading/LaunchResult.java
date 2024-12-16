/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.ITransformer;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;

public record LaunchResult(Map<String, IModFile> pluginContent,
        Map<String, IModFile> gameContent,
        List<ModLoadingIssue> issues,
        Map<String, ModFileInfo> loadedMods,
        List<ITransformer<?>> transformers,
        ClassLoader launchClassLoader) {}
