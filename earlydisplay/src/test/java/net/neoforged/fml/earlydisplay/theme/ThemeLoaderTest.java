/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void testDefaultThemeRoundtrip() throws IOException {
        var defaultTheme = Theme.createDefaultTheme();
        Path themePath = tempDir.resolve("theme-default.json");
        ThemeLoader.save(themePath, defaultTheme);

        var loadedTheme = ThemeLoader.load(tempDir, "default");
        assertThat(loadedTheme)
                .usingComparatorForType(RESOURCE_COMPARATOR, ThemeResource.class)
                .usingRecursiveComparison()
                .isEqualTo(defaultTheme);
    }

    @Test
    void testExtendingBuiltInTheme() throws Exception {
        // Write our theme that just overrides the background color
        var overriddenTheme = Map.of(
                "version", 1,
                "extends", "builtin:default",
                "colorScheme", Map.of(
                        "screenBackground", "#000000"));

        try (var writer = Files.newBufferedWriter(tempDir.resolve("theme-default.json"))) {
            new Gson().toJson(overriddenTheme, writer);
        }

        var defaultTheme = Theme.createDefaultTheme();
        var loadedTheme = ThemeLoader.load(tempDir, ThemeIds.DEFAULT);
        assertThat(loadedTheme)
                .usingComparatorForType(RESOURCE_COMPARATOR, ThemeResource.class)
                .usingRecursiveComparison()
                .ignoringFields("colorScheme.screenBackground")
                .isEqualTo(defaultTheme);
        assertEquals(ThemeColor.ofBytes(0, 0, 0), loadedTheme.colorScheme().screenBackground());
    }

    @Test
    void testOverridingBuiltInTheme() throws Exception {
        // Write our theme that just overrides the background color
        var overriddenTheme = Map.of(
                "version", 1,
                "colorScheme", Map.of(
                        "screenBackground", "#000000"));

        try (var writer = Files.newBufferedWriter(tempDir.resolve("theme-default.json"))) {
            new Gson().toJson(overriddenTheme, writer);
        }

        var loadedTheme = ThemeLoader.load(tempDir, ThemeIds.DEFAULT);
        assertThat(loadedTheme)
                .usingComparatorForType(RESOURCE_COMPARATOR, ThemeResource.class)
                .usingRecursiveComparison()
                .ignoringFields("colorScheme.screenBackground")
                .isEqualTo(new Theme(null, null, null, new ThemeColorScheme(
                        ThemeColor.ofBytes(0, 0, 0),
                        null,
                        null,
                        null), null, null));
    }

    private static final Comparator<ThemeResource> RESOURCE_COMPARATOR = Comparator.comparing(
            resource -> {
                if (resource == null) {
                    return null;
                }
                try {
                    return resource.toNativeBuffer(null).toByteArray();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            },
            (o1, o2) -> {
                if (o1 == null) {
                    return o2 == null ? 0 : -1;
                } else if (o2 == null) {
                    return 1;
                }
                if (o1.length != o2.length) {
                    return Integer.compareUnsigned(o1.length, o2.length);
                }
                for (int i = 0; i < o1.length; i++) {
                    if (o1[i] != o2[i]) {
                        return Integer.compareUnsigned(o1[i], o2[i]);
                    }
                }
                return 0;
            });
}
