/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

public class DeferredMixinConfigRegistration {
    private static final Logger LOG = LoggerFactory.getLogger("fml-mixin-setup");

    private static boolean added = false;

    record ConfigInfo(String fileName, @Nullable String modId) {}

    private static final List<ConfigInfo> mixinConfigs = new ArrayList<>();

    static {
        // Register our platform agent first
        List<String> agentClassNames = GlobalProperties.get(GlobalProperties.Keys.AGENTS);
        agentClassNames.add(FMLMixinPlatformAgent.class.getName());
        // Register the container (will use the platform agent)
        MixinBootstrap.getPlatform().addContainer(new FMLMixinContainerHandle());
    }

    public static void addMixinConfig(String config) {
        addMixinConfig(config, null);
    }

    public static void addMixinConfig(String config, @Nullable String modId) {
        if (added) {
            throw new IllegalStateException("Too late to add mixin configs!");
        }

        mixinConfigs.add(new ConfigInfo(config, modId));
    }

    static void registerConfigs() {
        added = true;
        mixinConfigs.forEach(cfg -> Mixins.addConfiguration(cfg.fileName()));
        final var configMap = Mixins.getConfigs().stream().collect(
                Collectors.toMap(Config::getName, Config::getConfig));
        mixinConfigs.forEach(cfg -> {
            if (cfg.modId() == null) return;

            final var config = configMap.get(cfg.fileName());
            if (config == null) {
                LOG.warn("Config file {} was not registered!", cfg.fileName());
            } else {
                config.decorate(FabricUtil.KEY_MOD_ID, cfg.modId());
            }
        });
        mixinConfigs.clear();
    }
}
