package net.neoforged.fml.config;

import net.neoforged.fml.ModContainer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This class provides access to all mod configs known to FML.
 * It can be used by mods that want to process all configs.
 * Configs are registered via {@link ModContainer#registerConfig(ModConfig.Type, IConfigSpec)}.
 */
public final class ModConfigs {
    // TODO: why is this a string return instead of a File or Path? Suspicious!
    public static String getConfigFileName(String modId, ModConfig.Type type) {
        var config = ConfigTracker.INSTANCE.configsByMod.getOrDefault(modId, Map.of()).get(type);
        return config == null ? null : config.getFullPath().toString();
    }

    public static Set<ModConfig> getConfigSet(ModConfig.Type type) {
        return Collections.unmodifiableSet(ConfigTracker.INSTANCE.configSets.get(type));
    }

    public static Map<String, ModConfig> getFileMap() {
        return Collections.unmodifiableMap(ConfigTracker.INSTANCE.fileMap);
    }
}
