/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

public record MaterializedThemeSprites(
        Texture progressBarBackground,
        Texture progressBarForeground,
        Texture progressBarIndeterminate) implements AutoCloseable {
    @Override
    public void close() {
        this.progressBarBackground.close();
        this.progressBarForeground.close();
        this.progressBarIndeterminate.close();
    }
}
