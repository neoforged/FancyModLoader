/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;

public class CustomSubclassModFileReader implements IModFileReader {
    public static final IdentifiableContent TRIGGER = new IdentifiableContent("CUSTOM_MODFILE_SUBCLASS_TRIGGER", "custom_modfile_subclass_trigger");

    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (jar.findFile(TRIGGER.relativePath()).isPresent()) {
            var modFile = mock(IModFile.class);
            when(modFile.getDiscoveryAttributes()).thenReturn(new ModFileDiscoveryAttributes(null, null, null, null));
            return modFile;
        }
        return null;
    }
}
