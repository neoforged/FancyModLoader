/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.net.URL;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.service.IClassProvider;

/**
 * Class provider for use under ModLauncher
 */
class FMLClassProvider implements IClassProvider {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ClassLoader pluginClassLoader;

    FMLClassProvider(ClassLoader pluginClassLoader) {
        this.pluginClassLoader = pluginClassLoader;
    }

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, true, pluginClassLoader);
        } catch (ClassNotFoundException e) {
            warnOnDeprecatedTclPluginLoad(name);
            return Class.forName(name, true, FMLLoader.getCurrent().getCurrentClassLoader());
        }
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        try {
            return Class.forName(name, initialize, pluginClassLoader);
        } catch (ClassNotFoundException e) {
            warnOnDeprecatedTclPluginLoad(name);
            return Class.forName(name, initialize, FMLLoader.getCurrent().getCurrentClassLoader());
        }
    }

    private static void warnOnDeprecatedTclPluginLoad(String name) {
        LOGGER.error("Mixin attempted to load class {} from the transforming classloader. This behavior is deprecated and may not continue to work; mixin config plugins should be provided from FMLModType=LIBRARY jars instead.", name);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, getClass().getClassLoader());
    }
}
