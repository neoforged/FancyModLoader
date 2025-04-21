/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeSerializerTest {
    @TempDir
    Path tempDir;

    @Test
    void testDefaultThemeRoundtrip() throws IOException {
        var defaultTheme = Theme.createDefaultTheme();
        Path themePath = tempDir.resolve("theme-default.json");
        ThemeSerializer.save(themePath, defaultTheme, false);

        var loadedTheme = ThemeSerializer.load(tempDir, "default");
        assertThat(loadedTheme)
                .usingComparatorForType(RESOURCE_COMPARATOR, ThemeResource.class)
                .usingRecursiveComparison()
                .isEqualTo(defaultTheme);
    }

    private static final Comparator<ThemeResource> RESOURCE_COMPARATOR = Comparator.comparing(
            resource -> {
                try {
                    return resource.toNativeBuffer().toByteArray();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            },
            (o1, o2) -> {
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
