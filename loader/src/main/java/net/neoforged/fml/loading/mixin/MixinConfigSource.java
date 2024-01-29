/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;

record MixinConfigSource(String id, String description) implements IMixinConfigSource {
    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
