/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

    public ModsTomlBuilder addMixinConfig(String name) {
        return addMixinConfig(name, null);
    }

    public ModsTomlBuilder addMixinConfig(String name, @Nullable String behaviorVersion) {
        return addMixinConfig(name, behaviorVersion, List.of());
    }

    public ModsTomlBuilder addMixinConfig(String name, @Nullable String behaviorVersion, List<String> requiredMods) {
        var configEntry = Config.inMemory();
        configEntry.set("config", name);
        if (behaviorVersion != null) {
            configEntry.set("behaviorVersion", behaviorVersion);
        }
        if (!requiredMods.isEmpty()) {
            configEntry.set("requiredMods", requiredMods);
        }
        config.add("mixins", List.of(configEntry));
        return this;
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
        List<Config> mods = config.get("mods");
        if (mods == null) {
            config.set("mods", mods = new ArrayList<>());
        }
        mods.add(modConfig);
        return this;
    }

    public ModsTomlBuilder addDependency(String modId, String targetModId, String targetVersionRange) {
        return addDependency(modId, targetModId, targetVersionRange, ignored -> {});
    }

    public ModsTomlBuilder addDependency(String modId, String targetModId, String targetVersionRange, Consumer<Config> customizer) {
        var dependency = Config.inMemory();
        dependency.set("modId", targetModId);
        dependency.set("versionRange", targetVersionRange);
        customizer.accept(dependency);

        List<Config> dependencies = config.get(List.of("dependencies", modId));
        if (dependencies == null) {
            config.set(List.of("dependencies", modId), dependencies = new ArrayList<>());
        }
        dependencies.add(dependency);
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
        return new IdentifiableContent("MODS_TOML", "META-INF/neoforge.mods.toml", bos.toByteArray());
    }
}
