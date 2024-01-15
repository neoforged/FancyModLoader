/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.event.lifecycle;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingStage;

public class FMLConstructModEvent extends ParallelDispatchEvent {
    public FMLConstructModEvent(final ModContainer container, final ModLoadingStage stage) {
        super(container, stage);
    }
}
