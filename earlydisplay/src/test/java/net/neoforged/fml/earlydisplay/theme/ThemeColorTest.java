/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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
}
