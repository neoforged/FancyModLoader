package net.neoforged.fml.loading.mixin;

import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.ArrayList;
import java.util.List;

public class DeferredMixinConfigRegistration {
    private static boolean added = false;
    private static final List<String> mixinConfigs = new ArrayList<>();

    static {
        // Register our platform agent first
        List<String> agentClassNames = GlobalProperties.get(GlobalProperties.Keys.AGENTS);
        agentClassNames.add(FMLMixinPlatformAgent.class.getName());
        // Register the container (will use the platform agent)
        MixinBootstrap.getPlatform().addContainer(new FMLMixinContainerHandle());
    }

    public static void addMixinConfig(String config) {
        if (added) {
            throw new IllegalStateException("Too late to add mixin configs!");
        }

        mixinConfigs.add(config);
    }

    static void registerConfigs() {
        added = true;
        mixinConfigs.forEach(Mixins::addConfiguration);
        mixinConfigs.clear();
    }
}
