/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * @param moduleName
 * @param nativeArchitectures If this is not empty, the jar should only load on one of these architectures.
 * @param forceBootLayer
 */
public record CachedMetadata(
        @Nullable String moduleName,
        List<NativeArchitecture> nativeArchitectures,
        boolean forceBootLayer) {}

/**
 * @param os
 * @param cpu Null if usable on any CPU architecture.
 */
record NativeArchitecture(NativeArchitectureOS os, @Nullable NativeArchitectureCPU cpu) {}
