/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.classloading;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ClassLoaderTestUtils {
    private ClassLoaderTestUtils() {}

    // Return the content of a resource retrieved via ClassLoader.getResource
    @Nullable
    public static String getResource(ClassLoader loader, String name) throws IOException {
        var resource = loader.getResource(name);
        if (resource == null) {
            return null;
        }
        try (var in = resource.openStream()) {
            return new String(in.readAllBytes());
        }
    }

    // Return the content of resources retrieved via ClassLoader.getResources
    public static List<String> getResources(ClassLoader loader, String name) throws IOException {
        var results = new ArrayList<String>();
        var enumeration = loader.getResources(name);
        while (enumeration.hasMoreElements()) {
            var resource = enumeration.nextElement();
            try (var in = resource.openStream()) {
                results.add(new String(in.readAllBytes()));
            }
        }
        return results;
    }
}
