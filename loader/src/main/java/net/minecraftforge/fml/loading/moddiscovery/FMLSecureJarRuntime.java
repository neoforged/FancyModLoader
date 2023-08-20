/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import cpw.mods.jarhandling.SecureJarRuntime;

import java.util.Set;

public class FMLSecureJarRuntime implements SecureJarRuntime {
    @Override
    public Set<String> ignoredRootPackages() {
        // Skip scanning assets and data for .class files for performance reasons
        return Set.of("META-INF", "assets", "data");
    }
}
