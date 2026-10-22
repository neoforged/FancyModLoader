/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.awt.Color;

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

    @Test
    void testToHsb() {
        assertArrayEquals(ThemeColor.WHITE.toHsb(), Color.RGBtoHSB(255, 255, 255, null));
        assertArrayEquals(ThemeColor.ofBytes(255, 0, 0).toHsb(), Color.RGBtoHSB(255, 0, 0, null));
        assertArrayEquals(ThemeColor.ofBytes(0, 255, 0).toHsb(), Color.RGBtoHSB(0, 255, 0, null));
        assertArrayEquals(ThemeColor.ofBytes(0, 0, 255).toHsb(), Color.RGBtoHSB(0, 0, 255, null));
    }

    @Test
    void testFromHsb() {
        assertEquals(ThemeColor.ofHsb(0f, 0f, 1f), ThemeColor.ofBytes(255, 255, 255));
        assertEquals(ThemeColor.ofHsb(0f, 1f, 1f), ThemeColor.ofBytes(255, 0, 0));
        assertEquals(ThemeColor.ofHsb(120f, 1f, 1f), ThemeColor.ofBytes(0, 255, 0));
        assertEquals(ThemeColor.ofHsb(240f, 1f, 1f), ThemeColor.ofBytes(0, 0, 255));
    }
}
