package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

class ConfigWatcher implements Runnable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ModConfig modConfig;
    private final CommentedFileConfig commentedFileConfig;
    private final ClassLoader realClassLoader;

    ConfigWatcher(final ModConfig modConfig, final CommentedFileConfig commentedFileConfig, final ClassLoader classLoader) {
        this.modConfig = modConfig;
        this.commentedFileConfig = commentedFileConfig;
        this.realClassLoader = classLoader;
    }

    @Override
    public void run() {
        // Force the regular classloader onto the special thread
        Thread.currentThread().setContextClassLoader(realClassLoader);

        modConfig.lock.lock();

        try {
            LOGGER.debug(ConfigTracker.CONFIG, "Config file {} changed, re-loading", modConfig.getFileName());
            if (this.modConfig.config != this.commentedFileConfig) {
                LOGGER.warn(ConfigTracker.CONFIG, "Config file {} has a mismatched loaded config. Expected {} but was {}.", modConfig.getFileName(), commentedFileConfig, modConfig.config);
            }
            ConfigTracker.loadConfig(this.modConfig, this.commentedFileConfig);
            this.modConfig.postConfigEvent(ModConfigEvent.Reloading::new);
        } finally {
            modConfig.lock.unlock();
        }
    }
}