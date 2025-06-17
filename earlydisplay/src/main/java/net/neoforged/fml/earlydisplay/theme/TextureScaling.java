/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

public sealed interface TextureScaling {
    /**
     * The intrinsic layout width of this image.
     * This is required to support images that are larger in physical pixels for High DPI.
     */
    int width();

    /**
     * The intrinsic layout height of this image.
     * This is required to support images that are larger in physical pixels for High DPI.
     */
    int height();

    boolean linearScaling();

    record Stretch(int width, int height, boolean linearScaling) implements TextureScaling {}

    record Tile(int width, int height, boolean linearScaling) implements TextureScaling {}

    record NineSlice(int width, int height, int left, int top, int right, int bottom, boolean stretchHorizontalFill,
            boolean stretchVerticalFill, boolean linearScaling) implements TextureScaling {}
}
