/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib;

import java.nio.file.Path;

public record InstallationComponents(
        Path minecraftCommonClassesRoot,
        Path minecraftClientClassesRoot,
        Path minecraftCommonResourcesRoot,
        Path minecraftClientResourcesRoot,
        Path neoforgeCommonClassesRoot,
        Path neoforgeClientClassesRoot,
        Path neoforgeCommonResourcesRoot,
        Path neoforgeClientResourcesRoot) {
    public static InstallationComponents productionJars(Path minecraft, Path neoforge) {
        return new InstallationComponents(
                minecraft,
                minecraft,
                minecraft,
                minecraft,
                neoforge,
                neoforge,
                neoforge,
                neoforge);
    }
}
