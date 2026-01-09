package net.neoforged.fml.loading.cache;

import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;

import java.io.IOException;

public class CacheUtils {

    public static void purgeCache() throws IOException {
        FileUtils.deleteDirectory(FMLPaths.CACHEDIR.get().toFile());
    }
}
