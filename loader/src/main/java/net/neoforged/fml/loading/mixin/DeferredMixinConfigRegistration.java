/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(forRemoval = true)
public class DeferredMixinConfigRegistration {
    private static final Logger LOG = LoggerFactory.getLogger("fml-mixin-setup");

    private static boolean added = false;

    record ConfigInfo(String fileName, @Nullable String modId) {}

    private static final List<ConfigInfo> mixinConfigs = new ArrayList<>();

    public static void addMixinConfig(String config) {
        addMixinConfig(config, null);
    }

    public static void addMixinConfig(String config, @Nullable String modId) {
        if (added) {
            throw new IllegalStateException("Too late to add mixin configs!");
        }

        mixinConfigs.add(new ConfigInfo(config, modId));
    }
    
    static List<ConfigInfo> legacyConfigs() {
        return mixinConfigs;
    }
}