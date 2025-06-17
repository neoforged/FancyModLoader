/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import net.neoforged.fml.earlydisplay.theme.ThemeResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WithOffScreenGLSurface.class)
class SimpleFontTest {
    private SimpleFont font;

    @BeforeEach
    void setUp() throws IOException {
        font = new SimpleFont(new ThemeResource("Monocraft.ttf"), null);
    }

    @AfterEach
    void tearDown() {
        font.close();
    }

    @Test
    void testMeasureText() {
        var measurement = font.measureText("Memory: 48/15784 MB (90.0%)  CPU: 0,7%");
        assertEquals(456, measurement.width());
        assertEquals(21, measurement.height());
    }
}
