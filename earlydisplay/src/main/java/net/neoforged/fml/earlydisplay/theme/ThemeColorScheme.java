/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

/**
 * @param screenBackground
 * @param text
 * @param memoryLowColor   The color to use for coloring the bar when resource usage is low.
 *                         The actual color will be interpolated between this and {@code highColor}.
 * @param memoryHighColor  The color to use for coloring the bar when resource usage is high.
 *                         The actual color will be interpolated between this and {@code lowColor}.
 */
public record ThemeColorScheme(
        ThemeColor screenBackground,
        ThemeColor text,
        ThemeColor memoryLowColor,
        ThemeColor memoryHighColor) {
    public static final ThemeColorScheme DEFAULT = new ThemeColorScheme(
            ThemeColor.ofBytes(239, 50, 61),
            ThemeColor.ofBytes(255, 255, 255),
            ThemeColor.ofBytes(0, 127, 0),
            ThemeColor.ofBytes(255, 127, 0));
}
