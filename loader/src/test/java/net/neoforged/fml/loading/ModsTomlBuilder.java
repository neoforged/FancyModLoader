/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import org.jetbrains.annotations.Nullable;

public class ModsTomlBuilder {
    private final CommentedConfig config = TomlFormat.newConfig();

    public ModsTomlBuilder() {
        config.add("mods", new ArrayList<>());
    }

    public ModsTomlBuilder unlicensedJavaMod() {
        license("unlicensed");
        javaMod();
        return this;
    }

    public ModsTomlBuilder license(@Nullable String license) {
        if (license == null) {
            config.remove("license");
        } else {
            config.set("license", license);
        }
        return this;
    }

    public ModsTomlBuilder javaMod() {
        return setLoader("javafml", "[1,)");
    }

    public ModsTomlBuilder setLoader(String loader, String versionRange) {
        config.set("modLoader", loader);
        config.set("loaderVersion", versionRange);
        return this;
    }

    public ModsTomlBuilder addMod(String modId) {
        return addMod(modId, "1.0");
    }

    public ModsTomlBuilder addMod(String modId, String version) {
        return addMod(modId, version, ignored -> {});
    }

    public ModsTomlBuilder addMod(String modId, String version, Consumer<Config> customizer) {
        var modConfig = Config.inMemory();
        modConfig.set("modId", modId);
        modConfig.set("version", version);
        customizer.accept(modConfig);
        config.getOrElse("mods", ArrayList::new).add(modConfig);
        return this;
    }

    public ModsTomlBuilder withRequiredFeatures(String modId, Map<String, String> features) {
        var featuresConfig = Config.inMemory();
        for (var entry : features.entrySet()) {
            featuresConfig.set(entry.getKey(), entry.getValue());
        }
        config.set(List.of("features", modId), featuresConfig);
        return this;
    }

    public ModsTomlBuilder customize(Consumer<CommentedConfig> consumer) {
        consumer.accept(config);
        return this;
    }

    public IdentifiableContent build() {
        var bos = new ByteArrayOutputStream();
        config.configFormat().createWriter().write(config, bos);
        return new IdentifiableContent("MODS_TOML", JarModsDotTomlModFileReader.MODS_TOML, bos.toByteArray());
    }
}
