/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.moddiscovery.locators.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;

/**
 * Provides simple libraries that are embedded in other mods.
 */
public class NestedLibraryModProvider implements IModFileReader {
    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes discoveryAttributes) {
        // We only consider jars that are contained in the context of another mod valid library targets,
        // since we assume those have been included deliberately. Loose jar files in the mods directory
        // are not considered, since those are likely to have been dropped in by accident.
        if (discoveryAttributes.parent() != null) {
            return IModFile.create(SecureJar.from(jar), JarModsDotTomlModFileReader::manifestParser, IModFile.Type.LIBRARY, discoveryAttributes);
        }

        return null;
    }

    @Override
    public String toString() {
        return "nested library mod provider";
    }
}
