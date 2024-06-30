/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import org.jetbrains.annotations.Nullable;

public record CachedMetadata(@Nullable String moduleName, boolean forceBootLayer) {}
