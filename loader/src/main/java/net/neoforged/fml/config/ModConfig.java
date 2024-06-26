/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.function.Function;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.StringUtils;
import org.jetbrains.annotations.Nullable;

public final class ModConfig {
    private final Type type;
    private final IConfigSpec spec;
    private final String fileName;
    private final ModContainer container;
    /**
     * When a file config is open: this is a {@link CommentedFileConfig}.
     * When a config was loaded from the server: this is a plain config.
     * Otherwise this is null.
     */
    @Nullable
    CommentedConfig config;

    ModConfig(Type type, IConfigSpec spec, ModContainer container, String fileName) {
        this.type = type;
        this.spec = spec;
        this.fileName = fileName;
        this.container = container;
    }

    public Type getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public IConfigSpec getSpec() {
        return spec;
    }

    public String getModId() {
        return container.getModId();
    }

    // TODO: remove from public API
    public Path getFullPath() {
        if (this.config instanceof FileConfig fileConfig) {
            return fileConfig.getNioPath();
        } else {
            throw new IllegalStateException("Cannot call getFullPath() on non-file config " + this.config + " at path " + getFileName());
        }
    }

    void postConfigEvent(Function<ModConfig, ModConfigEvent> constructor) {
        container.acceptEvent(constructor.apply(this));
    }

    public enum Type {
        /**
         * Common mod config for configuration that needs to be loaded on both environments.
         * Loaded on both servers and clients.
         * Stored in the global config directory.
         * Not synced.
         * Suffix is "-common" by default.
         */
        COMMON,
        /**
         * Client config is for configuration affecting the ONLY client state such as graphical options.
         * Only loaded on the client side.
         * Stored in the global config directory.
         * Not synced.
         * Suffix is "-client" by default.
         */
        CLIENT,
        /**
         * Server type config is configuration that is associated with a server instance.
         * Only loaded during server startup.
         * Stored in a server/save specific "serverconfig" directory.
         * Synced to clients during connection.
         * Suffix is "-server" by default.
         */
        SERVER,
        /**
         * Startup configs are for configurations that need to run as early as possible.
         * Loaded as soon as the config is registered to FML.
         * Please be aware when using them, as using these configs to enable/disable registration and anything that must be present on both sides
         * can cause clients to have issues connecting to servers with different config values.
         * Stored in the global config directory.
         * Not synced.
         * Suffix is "-startup" by default.
         */
        STARTUP;

        public String extension() {
            return StringUtils.toLowerCase(name());
        }
    }
}
