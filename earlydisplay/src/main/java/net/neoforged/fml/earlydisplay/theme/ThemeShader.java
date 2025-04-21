/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

public record ThemeShader(
        ThemeResource vertexShader,
        ThemeResource fragmentShader) {
    public static final ThemeShader DEFAULT_GUI = new ThemeShader(
            new ClasspathResource("net/neoforged/fml/earlydisplay/gui.vert"),
            new ClasspathResource("net/neoforged/fml/earlydisplay/gui.frag"));
    public static final ThemeShader DEFAULT_FONT = new ThemeShader(
            new ClasspathResource("net/neoforged/fml/earlydisplay/gui.vert"),
            new ClasspathResource("net/neoforged/fml/earlydisplay/gui_font.frag"));
    public static final ThemeShader DEFAULT_COLOR = new ThemeShader(
            new ClasspathResource("net/neoforged/fml/earlydisplay/gui.vert"),
            new ClasspathResource("net/neoforged/fml/earlydisplay/gui_color.frag"));
}
