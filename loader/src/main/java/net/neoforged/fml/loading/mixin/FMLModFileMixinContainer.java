/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.util.Collection;
import java.util.List;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants;

public class FMLModFileMixinContainer implements IContainerHandle {
    private final ModFile modFile;

    public FMLModFileMixinContainer(ModFile modFile) {
        this.modFile = modFile;
    }

    @Override
    public String getAttribute(String name) {
        if (Constants.ManifestAttributes.MIXINCONFIGS.equals(name)) {
            var mixinConfigs = modFile.getMixinConfigs();
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
        var modFileInfo = modFile.getModFileInfo();
        if (!modFileInfo.getMods().isEmpty()) {
            return modFileInfo.getMods().getFirst().getModId();
        } else {
            return modFileInfo.moduleName();
        }
    }

    @Override
    public String getDescription() {
        return "FML mod file (" + modFile.getFileName() + ")";
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
