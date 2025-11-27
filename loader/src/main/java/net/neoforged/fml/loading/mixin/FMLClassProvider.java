/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.net.URL;
import org.spongepowered.asm.service.IClassProvider;

/**
 * Class provider for use under ModLauncher
 */
class FMLClassProvider implements IClassProvider {
    private final ClassLoader transformingLoader;

    FMLClassProvider(ClassLoader transformingLoader) {
        this.transformingLoader = transformingLoader;
    }

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return transformingLoader.loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, transformingLoader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, getClass().getClassLoader());
    }
}
