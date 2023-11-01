package net.neoforged.fml.loading.mixin;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;

import java.util.Collection;
import java.util.List;

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
}
