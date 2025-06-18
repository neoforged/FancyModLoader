/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that the bundled themes can all be loaded without errors, and that the bundled default theme isn't outdated.
 */
public class BundledThemesTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @MethodSource("builtInThemeIds")
    void testLoadBuiltInTheme(String themeId) {
        Assertions.assertDoesNotThrow(() -> ThemeLoader.load(null, themeId));
    }

    /**
     * Checks that the theme in the resources directory matches what we'd get from exporting the in-code representation.
     * If this test fails, run {@link net.neoforged.fml.earlydisplay.ExportDefaultTheme}.
     */
    @Test
    public void testDefaultThemeMatchesInCodeVersion() throws Exception {
        Path expectedTheme = tempDir.resolve("expected.json");
        ThemeLoader.save(expectedTheme, Theme.createDefaultTheme());
        var expectedThemeContent = Files.readString(expectedTheme, StandardCharsets.UTF_8);
        var expectedTree = prettyPrint(normalize(JsonParser.parseString(expectedThemeContent)));

        String actualThemeContent;
        try (var in = getClass().getResourceAsStream("/net/neoforged/fml/earlydisplay/theme/theme-default.json")) {
            Objects.requireNonNull(in, "built-in theme is missing?");
            actualThemeContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var actualTree = prettyPrint(normalize(JsonParser.parseString(actualThemeContent)));

        assertEquals(actualTree, expectedTree);
    }

    public static String[] builtInThemeIds() throws Exception {
        var fields = ThemeIds.class.getFields();
        return Arrays.stream(fields).map(f -> {
            try {
                return (String) f.get(null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).toArray(String[]::new);
    }

    private static JsonElement normalize(JsonElement element) {
        if (element instanceof JsonObject obj) {
            // Alphabetically sort the keys
            var t = new ArrayList<>(obj.keySet());
            Collections.sort(t);

            var newObject = new JsonObject();
            for (String s : t) {
                newObject.add(s, normalize(obj.get(s)));
            }
            return newObject;
        } else if (element instanceof JsonArray arr) {
            var newArr = new JsonArray();
            for (var childEl : arr) {
                newArr.add(normalize(childEl));
            }
            return newArr;
        } else {
            return element;
        }
    }

    private static String prettyPrint(JsonElement el) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(el);
    }
}
