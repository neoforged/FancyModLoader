/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;

public class FMLMixinPlatformAgent extends MixinPlatformAgentAbstract {
    @Override
    public AcceptResult accept(MixinPlatformManager manager, IContainerHandle handle) {
        if (handle instanceof FMLMixinContainerHandle) {
            return AcceptResult.ACCEPTED;
        }
        return AcceptResult.REJECTED;
    }

    @Override
    public void prepare() {
        // Load all the mixin configs!!
        DeferredMixinConfigRegistration.registerConfigs();
    }
}
