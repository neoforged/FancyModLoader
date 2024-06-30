/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import java.io.File;
import java.io.Serializable;

public record DiscoveredFile(File file, FileCacheKey cacheKey) implements Serializable {
    public static DiscoveredFile of(File file) {
        return new DiscoveredFile(file, new FileCacheKey(file.getName(), file.length(), file.lastModified()));
    }

    public Object[] parcel() {
        return new Object[] { file, cacheKey.parcel() };
    }

    public static DiscoveredFile unparcel(Object[] parcel) {
        return new DiscoveredFile(
                (File) parcel[0],
                FileCacheKey.unparcel((Object[]) parcel[1]));
    }
}
