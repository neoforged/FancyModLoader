/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.util.Optional;

/**
 * Finds Version data from a package, with possible default values
 */
public class JarVersionLookupHandler {
    public static Optional<String> getVersion(final Class<?> clazz) {
        if (clazz.getModule() != null && clazz.getModule().getDescriptor() != null) {
            return clazz.getModule().getDescriptor().rawVersion();
        }
        return Optional.empty();
    }
}
