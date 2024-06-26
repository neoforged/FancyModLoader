/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

/**
 * A config spec is responsible for interpreting (loading, correcting) raw {@link CommentedConfig}s from NightConfig.
 *
 * <p>NeoForge provides {@code ModConfigSpec} for the most common cases.
 */
public interface IConfigSpec {
    /**
     * Returns the default content of the config.
     */
    UnmodifiableCommentedConfig getDefaultConfig();

    /**
     * Checks that a config is correct.
     * If this function returns {@code false}, a backup is made then the config is fed through {@link #correct}.
     */
    boolean isCorrect(UnmodifiableCommentedConfig config);

    /**
     * Corrects a config. Only called if {@link #isCorrect} returned {@code false}.
     *
     * <p>This can be used to fix broken entries, add back missing entries or comments, etc...
     * The returned config will be saved to disk.
     *
     * <p>The config should not be loaded into the spec yet. A call to {@link #load} will be made for that.
     *
     * <p>The config should not be saved yet. FML will take care of that after this method.
     */
    void correct(CommentedFileConfig config);

    /**
     * Updates the spec's data to a config.
     * This is called on loading and on reloading.
     * The config is guaranteed to be valid according to {@link #isCorrect}.
     */
    void load(UnmodifiableCommentedConfig config);
}
