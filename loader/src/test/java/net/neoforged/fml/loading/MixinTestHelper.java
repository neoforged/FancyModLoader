/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.util.Collection;
import java.util.Map;
import net.neoforged.fml.loading.mixin.FMLMixinService;
import org.junit.jupiter.api.BeforeEach;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;

public interface MixinTestHelper {
    @BeforeEach
    default void cleanUpMixins() {
        // What a mess. If we never successfully initialized Mixin
        // we cannot even load the class, since it'd try to initialize a default service.
        // See MixinFacade for details
        if (System.getProperty("mixin.service") == null) {
            return;
        }

        try {
            Mixins.getConfigs().clear();
        } catch (ServiceNotAvailableError ignored) {}

        clearCollection(Config.class, null, "allConfigs");
        clearCollection(Mixins.class, null, "registeredConfigs");

        var service = (FMLMixinService) MixinService.getService();
        service.clearMixinContainers();

        // Clear root platform manager
        var platform = MixinBootstrap.getPlatform();
        clearCollection(MixinPlatformManager.class, platform, "containers");
        setField(MixinPlatformManager.class, platform, "injected", false);
        setField(MixinBootstrap.class, null, "platform", null); // Make MixinBootstrap call init on the platform again

        gotoPhase(MixinEnvironment.Phase.PREINIT);
    }

    private void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            var m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, phase);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> void setField(Class<?> clazz, T instance, String fieldName, Object value) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field value " + fieldName, e);
        }
    }

    private static <T> void clearCollection(Class<T> clazz, T instance, String fieldName) {
        Object obj;
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            obj = field.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to get field value " + fieldName, e);
        }
        switch (obj) {
            case Map<?, ?> map -> map.clear();
            case Collection<?> collection -> collection.clear();
            case null -> {}
            default -> throw new IllegalStateException("Don't know how to clear " + obj);
        }
    }
}
