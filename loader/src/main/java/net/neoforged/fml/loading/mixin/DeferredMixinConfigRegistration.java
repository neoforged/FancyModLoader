/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import net.neoforged.neoforgespi.locating.IModFile;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;

import java.util.ArrayList;
import java.util.List;

public class DeferredMixinConfigRegistration {
    private static boolean added = false;
    private static final List<MixinToAdd> mixinConfigs = new ArrayList<>();

    static {
        // Register our platform agent first
        List<String> agentClassNames = GlobalProperties.get(GlobalProperties.Keys.AGENTS);
        agentClassNames.add(FMLMixinPlatformAgent.class.getName());
        // Register the container (will use the platform agent)
        MixinBootstrap.getPlatform().addContainer(new FMLMixinContainerHandle());
    }

    private record MixinToAdd(String config, IMixinConfigSource source) {}

    public static void addCommandLineMixinConfig(String config) {
        addMixinConfig(config, new MixinConfigSource(config, "Mixin config passed via --fml.mixinConfig " + config));
    }

    public static void addModMixinConfig(String config, IModFile file) {
        String sourceId;
        if (file.getModInfos().size() == 1) {
            // Single mod, use the modid
            sourceId = file.getModInfos().get(0).getModId();
        } else {
            // Else use file name...
            sourceId = file.getFileName();
        }

        addMixinConfig(config, new MixinConfigSource(sourceId, "Mixin config loaded from mod file " + file.getFilePath()));
    }

    private static void addMixinConfig(String config, IMixinConfigSource source) {
        if (added) {
            throw new IllegalStateException("Too late to add mixin configs!");
        }

        mixinConfigs.add(new MixinToAdd(config, source));
    }

    static void registerConfigs() {
        added = true;
        mixinConfigs.forEach(c -> {
            Mixins.addConfiguration(c.config(), c.source());
        });
        mixinConfigs.clear();
    }
}
