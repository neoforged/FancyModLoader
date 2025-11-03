/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import net.neoforged.fml.loading.moddiscovery.ModFile;

/**
 * The result of discovering NeoForge and Minecraft.
 */
public record GameDiscoveryResult(ModFile neoforge, ModFile minecraft, boolean production) {}
