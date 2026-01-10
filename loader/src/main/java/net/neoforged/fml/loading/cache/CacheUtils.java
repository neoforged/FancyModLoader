/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.cache;

import java.io.IOException;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;

public class CacheUtils {
    public static void purgeCache() throws IOException {
        FileUtils.deleteDirectory(FMLPaths.CACHEDIR.get().toFile());
    }
}
