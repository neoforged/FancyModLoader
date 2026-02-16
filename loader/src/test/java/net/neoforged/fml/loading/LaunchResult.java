/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;

public record LaunchResult(Map<String, JarContents> pluginLayerModules,
        Map<String, JarContents> gameLayerModules,
        List<ModLoadingIssue> issues,
        Map<String, ModFileInfo> loadedMods,
        ClassLoader launchClassLoader) {}
