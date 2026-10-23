/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThemeColorTest {
    @Test
    void testToArgb() {
        assertEquals(-1, ThemeColor.WHITE.toArgb());
        assertEquals(0xFFFF0000, new ThemeColor(1, 0, 0, 1).toArgb());
        assertEquals(0xFF00FF00, new ThemeColor(0, 1, 0, 1).toArgb());
        assertEquals(0xFF0000FF, new ThemeColor(0, 0, 1, 1).toArgb());
    }

    @Test
    void testFromArgb() {
        assertEquals(ThemeColor.WHITE, ThemeColor.ofRgb(0xFFFFFF));
        assertEquals(ThemeColor.ofBytes(255, 0, 0), ThemeColor.ofArgb(0xFFFF0000));
        assertEquals(ThemeColor.ofBytes(0, 255, 0), ThemeColor.ofArgb(0xFF00FF00));
        assertEquals(ThemeColor.ofBytes(0, 0, 255), ThemeColor.ofArgb(0xFF0000FF));
    }

    @ParameterizedTest
    @CsvSource({
            "255, 255, 255",
            "127, 127, 127",
            "255, 0, 0",
            "127, 0, 0",
            "0, 255, 0",
            "0, 127, 0",
            "0, 0, 255",
            "0, 0, 127"
    })
    void testToHsb(int r, int g, int b) {
        assertArrayEquals(ThemeColor.ofBytes(r, g, b).toHsb(), Color.RGBtoHSB(r, g, b, null));
    }

    @ParameterizedTest
    @CsvSource({
            "255, 255, 255",
            "127, 127, 127",
            "255, 0, 0",
            "127, 0, 0",
            "0, 255, 0",
            "0, 127, 0",
            "0, 0, 255",
            "0, 0, 127"
    })
    void testFromHsb(int r, int g, int b) {
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        assertEquals(ThemeColor.ofHsb(hsb[0] * 360, hsb[1], hsb[2]), ThemeColor.ofBytes(r, g, b));
    }
}
