/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MavenVersionTranslatorTest {
    @Test
    void testArtifactVersionToString() {
        assertEquals("1.0.5-abc", MavenVersionTranslator.artifactVersionToString(new DefaultArtifactVersion("1.0.5-abc")));
    }

    // Examples taken straight from here: https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
    @ParameterizedTest
    @CsvSource(textBlock = """
            *|any
            (,1.0]|1.0 or below
            (,1.0)|below 1.0
            [1.0]|1.0
            [1.0,)|1.0 or above
            (1.0,)|above 1.0
            (1.0,2.0)|between 1.0 and 2.0 (exclusive)
            [1.0,2.0]|between 1.0 and 2.0 (inclusive)
            (1.0,2.0]|above 1.0, and 2.0 or below
            [1.0,2.0)|1.0 or above, and below 2.0
            (,1.0],[1.2,)|1.0 or below, 1.2 or above
            (,1.1),(1.1,)|below 1.1, above 1.1
            """, delimiter = '|')
    void testVersionRangeToString(String input, String expected) throws Exception {
        VersionRange versionRange = VersionRange.createFromVersionSpec(input);
        assertEquals(expected, MavenVersionTranslator.versionRangeToString(versionRange));
    }
}
