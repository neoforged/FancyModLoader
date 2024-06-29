/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import java.io.File;

public record DiscoveredFile(File file, FileCacheKey cacheKey) {
    public static DiscoveredFile of(File file) {
        return new DiscoveredFile(file, new FileCacheKey(file.getName(), file.length(), file.lastModified()));
    }
}
