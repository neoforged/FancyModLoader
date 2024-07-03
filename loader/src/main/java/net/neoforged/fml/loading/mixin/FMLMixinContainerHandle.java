/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.util.Collection;
import java.util.List;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;

/**
 * Container handle representing all of FML's mixin configs.
 * No attribute because we directly load the mixin configs in {@link FMLMixinPlatformAgent}.
 */
public class FMLMixinContainerHandle implements IContainerHandle {
    @Override
    public String getAttribute(String name) {
        return null;
    }

    @Override
    public Collection<IContainerHandle> getNestedContainers() {
        return List.of();
    }

    @Override
    public String getId() {
        return "fml";
    }

    @Override
    public String getDescription() {
        return "FancyModLoader";
    }
}
