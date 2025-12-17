/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.ApiStatus;

/**
 * Provides context for various FML plugins about the current launch operation.
 */
@ApiStatus.NonExtendable
public interface ILaunchContext extends LocatedPaths {
    Dist getRequiredDistribution();

    /**
     * The game directory.
     */
    Path gameDirectory();

    String getMinecraftVersion();

    String getNeoForgeVersion();
}
