package net.neoforged.fml.earlydisplay.render;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.Theme;

/**
 * A themes resources loaded for rendering at runtime.
 */
public record MaterializedTheme(
        Theme theme,
        Map<String, SimpleFont> fonts,
        Map<String, ElementShader> shaders) implements AutoCloseable {
    public static MaterializedTheme materialize(Theme theme) {
        return new MaterializedTheme(
                theme,
                loadFonts(theme),
                loadShaders(theme));
    }

    private static Map<String, ElementShader> loadShaders(Theme theme) {
        var shaders = new HashMap<String, ElementShader>(theme.shaders().size());
        for (var entry : theme.shaders().entrySet()) {
            var shader = ElementShader.create(
                    entry.getKey(),
                    entry.getValue().vertexShader(),
                    entry.getValue().fragmentShader());
            shaders.put(entry.getKey(), shader);
        }
        return shaders;
    }

    private static Map<String, SimpleFont> loadFonts(Theme theme) {
        var fonts = new HashMap<String, SimpleFont>(theme.fonts().size());
        for (var entry : theme.fonts().entrySet()) {
            try {
                fonts.put(entry.getKey(), new SimpleFont(entry.getValue(), 1));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load font " + entry.getKey(), e);
            }
        }
        return fonts;
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
