/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MavenCoordinateTest {
    @ParameterizedTest
    @ValueSource(strings = { "g:a:v",
            "g:a:v:classifier",
            "g:a:v@zip",
            "g:a:v:classifier@zip" })
    void testParseToStringRoundtrip(String compactForm) {
        assertEquals(compactForm, MavenCoordinate.parse(compactForm).toString());
    }

    @Test
    void testNullExtensionCoercion() {
        var coordinate = new MavenCoordinate("g", "a", null, "", "v");
        assertEquals("", coordinate.extension());
    }

    @Test
    void testNullClassifierCoercion() {
        var coordinate = new MavenCoordinate("g", "a", "", null, "v");
        assertEquals("", coordinate.classifier());
    }

    @ParameterizedTest
    @ValueSource(strings = { "g:a:c:v:extra", "g:a", "g:a:v@t@t" })
    void testParseInvalidForms(String value) {
        assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse(value));
    }

    @Test
    void testParseGAV() {
        assertEquals(new MavenCoordinate("g", "a", "", "", "v"), MavenCoordinate.parse("g:a:v"));
    }

    @Test
    void testParseGAVWithClassifier() {
        assertEquals(new MavenCoordinate("g", "a", "", "classifier", "v"), MavenCoordinate.parse("g:a:v:classifier"));
    }

    @Test
    void testParseGAVWithExtension() {
        assertEquals(new MavenCoordinate("g", "a", "zip", "", "v"), MavenCoordinate.parse("g:a:v@zip"));
    }

    @Test
    void testParseGAVWithClassifierAndExtension() {
        assertEquals(new MavenCoordinate("g", "a", "zip", "classifier", "v"), MavenCoordinate.parse("g:a:v:classifier@zip"));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            g:a:v, g/a/v/a-v.jar
            g.h.j:a:v, g/h/j/a/v/a-v.jar
            g:a:v:c, g/a/v/a-v-c.jar
            g.h.j:a:v:c, g/h/j/a/v/a-v-c.jar
            g:a:v@zip, g/a/v/a-v.zip
            g:a:v:c@zip, g/a/v/a-v-c.zip
            """)
    void testToRelativeRepositoryPath(String compactForm, String expectedPath) {
        assertEquals(Paths.get(expectedPath), MavenCoordinate.parse(compactForm).toRelativeRepositoryPath());
    }
}
