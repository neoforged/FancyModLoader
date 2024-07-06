package net.neoforged.fml.loading.mixin;

import net.neoforged.fml.loading.moddiscovery.ModFile;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants;

import java.util.Collection;
import java.util.List;

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
