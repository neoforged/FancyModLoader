/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeResource;
import net.neoforged.fml.earlydisplay.theme.ThemeShader;
import net.neoforged.fml.earlydisplay.theme.ThemeSprites;

/**
 * A themes resources loaded for rendering at runtime.
 */
public record MaterializedTheme(
        Theme theme,
        Map<String, SimpleFont> fonts,
        Map<String, ElementShader> shaders,
        MaterializedThemeSprites sprites) implements AutoCloseable {
    public static MaterializedTheme materialize(Theme theme) {
        return new MaterializedTheme(
                theme,
                loadFonts(theme.fonts()),
                loadShaders(theme.shaders()),
                loadSprites(theme.sprites()));
    }

    private static Map<String, ElementShader> loadShaders(Map<String, ThemeShader> themeShaders) {
        var shaders = new HashMap<String, ElementShader>(themeShaders.size());
        for (var entry : themeShaders.entrySet()) {
            var shader = ElementShader.create(
                    entry.getKey(),
                    entry.getValue().vertexShader(),
                    entry.getValue().fragmentShader());
            shaders.put(entry.getKey(), shader);
        }
        return shaders;
    }

    private static Map<String, SimpleFont> loadFonts(Map<String, ThemeResource> themeFonts) {
        var fonts = new HashMap<String, SimpleFont>(themeFonts.size());
        for (var entry : themeFonts.entrySet()) {
            try {
                fonts.put(entry.getKey(), new SimpleFont(entry.getValue(), 1));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load font " + entry.getKey(), e);
            }
        }
        return fonts;
    }

    private static MaterializedThemeSprites loadSprites(ThemeSprites sprites) {
        return new MaterializedThemeSprites(
                Texture.create(sprites.progressBarBackground()),
                Texture.create(sprites.progressBarForeground()),
                Texture.create(sprites.progressBarIndeterminate()));
    }

    public SimpleFont getFont(String fontId) {
        var font = fonts.getOrDefault(fontId, fonts.get(Theme.FONT_DEFAULT));
        if (font == null) {
            throw new IllegalStateException("Theme does not contain a default font. Available fonts: " + fonts.keySet());
        }
        return font;
    }

    public ElementShader getShader(String shaderId) {
        var shader = shaders.get(shaderId);
        if (shader == null) {
            throw new IllegalArgumentException("Missing shader definition in theme for " + shaderId);
        }
        return shader;
    }

    @Override
    public void close() {
        shaders.values().forEach(ElementShader::close);
    }
}
