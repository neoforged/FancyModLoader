package net.neoforged.fml.loading.mixin;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants;

import java.util.Collection;
import java.util.List;

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
