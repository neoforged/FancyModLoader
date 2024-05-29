/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

import org.jetbrains.annotations.Nullable;

/**
 * Provides information on whether an enum was extended and how many entries are vanilla vs. modded
 *
 * @param extended     Whether this enum had additional entries added to it
 * @param vanillaCount How many entries the enum originally contained
 * @param totalCount   How many entries the enum contains after extension
 * @param netCheck     Whether the enum needs to be checked for network compatibility
 */
public record ExtensionInfo(boolean extended, int vanillaCount, int totalCount, @Nullable NetworkedEnum.NetworkCheck netCheck) {}
