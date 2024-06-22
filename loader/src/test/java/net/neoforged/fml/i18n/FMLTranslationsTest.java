/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Objects;
import java.util.Optional;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FMLTranslationsTest {
    @Test
    void testVanillaFormatSpecifiers() {
        Assertions.assertThat(FMLTranslations.parseFormat("this is a translation %s %s", "a", "b"))
                .isEqualTo("this is a translation a b");

        Assertions.assertThat(FMLTranslations.parseFormat("this is a translation %2$s %1$s", "a", "b"))
                .isEqualTo("this is a translation b a");
    }

    @Test
    void testDoublePercent() {
        Assertions.assertThat(FMLTranslations.parseFormat("%%")).isEqualTo("%");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            id,The Mod Id
            name,The Mod Name
            """)
    void testFormatModInfo(String field, String expected) {
        var mod = mock(IModInfo.class);
        when(mod.getModId()).thenReturn("The Mod Id");
        when(mod.getDisplayName()).thenReturn("The Mod Name");
        assertEquals(expected, FMLTranslations.parseFormat("{0,modinfo," + field + "}", mod));
    }

    @Test
    void testFormatModInfoInvalidArg() {
        assertEquals("", FMLTranslations.parseFormat("{0,modinfo,id}", new Object()));
    }

    @Test
    void testFormatModInfoMissingArg() {
        assertEquals("", FMLTranslations.parseFormat("{0,modinfo}", mock(IModInfo.class)));
    }

    @Test
    void testFormatLower() {
        assertEquals("abc", FMLTranslations.parseFormat("{0,lower}", "ABC"));
    }

    @Test
    void testFormatLowerCallsToString() {
        var arg = new Object() {
            @Override
            public String toString() {
                return "ABC";
            }
        };
        assertEquals("abc", FMLTranslations.parseFormat("{0,lower}", arg));
    }

    @Test
    void testFormatUpper() {
        assertEquals("ABC", FMLTranslations.parseFormat("{0,upper}", "abc"));
    }

    @Test
    void testFormatUpperCallsToString() {
        var arg = new Object() {
            @Override
            public String toString() {
                return "abc";
            }
        };
        assertEquals("ABC", FMLTranslations.parseFormat("{0,upper}", arg));
    }

    @Test
    void testFormatExceptionMissingArg() {
        var e = new IllegalArgumentException("Message of the Exception");
        assertEquals("", FMLTranslations.parseFormat("{0,exc}", e));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            msg,java.lang.IllegalArgumentException: Message of the Exception
            cls,java.lang.IllegalArgumentException
            """)
    void testFormatExceptionMessage(String field, String expected) {
        var e = new IllegalArgumentException("Message of the Exception");
        assertEquals(expected, FMLTranslations.parseFormat("{0,exc," + field + "}", e));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            msg,java.lang.IllegalArgumentException: Message of the Exception
            cls,java.lang.IllegalArgumentException
            """)
    void testVersionRange(String field, String expected) {
        var e = new IllegalArgumentException("Message of the Exception");
        assertEquals(expected, FMLTranslations.parseFormat("{0,exc," + field + "}", e));
    }

    @Test
    void testFormatVersionRange() throws Exception {
        assertEquals("any", FMLTranslations.parseFormat("{0,vr}", VersionRange.createFromVersionSpec("*")));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false,booleantestfeature=false
            true,booleantestfeature=true
            TRUE,booleantestfeature=true
            """)
    void testFormatBooleanFeatureBound(String bound, String expected) {
        var feature = new ForgeFeature.BooleanFeatureTest(IModInfo.DependencySide.CLIENT, false);
        ForgeFeature.registerFeature("booleantestfeature", feature);
        assertEquals(expected, FMLTranslations.parseFormat("{0,featurebound}", new ForgeFeature.Bound("booleantestfeature", bound, null)));
    }

    /**
     * This just checks that it invokes the maven formatter. So this doesn't get full coverage of that.
     */
    @Test
    void testFormatVersionRangeFeatureBound() {
        var feature = new ForgeFeature.VersionFeatureTest(IModInfo.DependencySide.CLIENT, new DefaultArtifactVersion("0"));
        ForgeFeature.registerFeature("versiontestfeature", feature);
        assertEquals("versiontestfeature any", FMLTranslations.parseFormat("{0,featurebound}", new ForgeFeature.Bound("versiontestfeature", "*", null)));
    }

    @Test
    void testFormatOtherFeatureBound() {
        assertEquals("missingfeature=\"123\"", FMLTranslations.parseFormat("{0,featurebound}", new ForgeFeature.Bound("missingfeature", "123", null)));
    }

    @Test
    void testFormatI18n() {
        assertEquals("above BLAH", FMLTranslations.parseFormat("{0,i18n,fml.messages.version.restriction.lower.exclusive}", "BLAH"));
    }

    @Test
    void testFormatI18nTranslate() {
        assertEquals("any", FMLTranslations.parseFormat("{0,i18ntranslate}", "fml.messages.version.restriction.any"));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            null,Proceed at your own risk
            abc,abc
            """)
    void testFormatOrNull(String arg, String expected) {
        assertEquals(expected, FMLTranslations.parseFormat("{0,ornull,fml.modloadingissue.discouragedmod.proceed}", arg));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            ,,
            prefix,,
            ,abc,abc
            prefix,abc,prefixabc
            """)
    void testFormatOptional(String prefix, String arg, String expected) {
        if (prefix != null) {
            prefix = "," + prefix;
        } else {
            prefix = "";
        }
        expected = Objects.requireNonNullElse(expected, "");
        assertEquals(expected, FMLTranslations.parseFormat("{0,optional" + prefix + "}", Optional.ofNullable(arg)));
    }
}
