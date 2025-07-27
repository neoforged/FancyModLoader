/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.service.IMixinServiceBootstrap;

@ApiStatus.Internal
public class FMLMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "fml";
    }

    @Override
    public String getServiceClassName() {
        return FMLMixinService.class.getName();
    }

    @Override
    public void bootstrap() {}
}
