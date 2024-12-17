/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import org.jetbrains.annotations.Nullable;

public class PluginClassLoader extends URLClassLoader {
    private static final String[] WHITELISTED_PACKAGE_PREFIXES = new String[] {
            "java.", "net.neoforged.fml."
    };

    static {
        registerAsParallelCapable();
    }

    public PluginClassLoader(String name, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(name, urls, parent, factory);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (var prefix : WHITELISTED_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return getParent().loadClass(name);
            }
        }

        return findClass(name);
    }

    @Nullable
    @Override
    public URL getResource(String name) {
        for (var prefix : WHITELISTED_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return getParent().getResource(name);
            }
        }

        return findResource(name);
    }
}
