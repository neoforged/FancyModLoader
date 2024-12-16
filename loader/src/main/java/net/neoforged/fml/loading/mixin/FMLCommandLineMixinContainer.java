/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.util.Collection;
import java.util.List;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants;

public class FMLCommandLineMixinContainer implements IContainerHandle {
    private final List<String> mixinConfigs;

    public FMLCommandLineMixinContainer(List<String> mixinConfigs) {
        this.mixinConfigs = mixinConfigs;
    }

    @Override
    public String getAttribute(String name) {
        if (Constants.ManifestAttributes.MIXINCONFIGS.equals(name)) {
            if (!mixinConfigs.isEmpty()) {
                return String.join(",", mixinConfigs);
            }
        }
        return null;
    }

    @Override
    public Collection<IContainerHandle> getNestedContainers() {
        return List.of();
    }

    @Override
    public String getId() {
        return "fml-commandline";
    }

    @Override
    public String getDescription() {
        return "FML command line extra mixins";
    }
}
