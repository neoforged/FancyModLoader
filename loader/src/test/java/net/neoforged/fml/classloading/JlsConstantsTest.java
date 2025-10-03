/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.classloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JlsConstantsTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            '',''
            '','Class'
            '','.'
            '','.Class'
            'pkg','pkg.Class'
            'pkg.pkg.pkg','pkg.pkg.pkg.Class'
            """)
    void testGetPackageName(String expectedPackage, String typeName) {
        assertEquals(expectedPackage, JlsConstants.getPackageName(typeName));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pkg",
            "Class",
            "pkg.Class",
            "pkg.pkg.Class",
            "pkg.pkg.Class.InnerClass",
            "pkg.pkg.pkg"
    })
    void testValidTypeNames(String typeName) {
        assertTrue(JlsConstants.isTypeName(typeName));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Reserved keyword
            "package",
            // Reserved keyword in middle package
            "pkg.package.Class",
            // Empty package segment
            "package..Class",
            // Empty package segment
            ".Class",
            // Empty trailing package segment
            "Class."
    })
    void testInvalidTypeNames(String typeName) {
        assertFalse(JlsConstants.isTypeName(typeName));
    }
}
