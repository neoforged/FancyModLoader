/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

/**
 * Used to identify the source of a {@link IModFile}.
 */
public sealed interface IModFileSource permits IModFileReader, IModFileProvider {}
