/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ForgeFeatureTest {
    @Mock
    IModInfo modInfo;

    ForgeFeature.Bound boundForMissingFeature;

    @BeforeEach
    void setUp() {
        boundForMissingFeature = new ForgeFeature.Bound("some_feature_that_does_not_exist", "*", modInfo);
    }

    @Test
    void testForMissingFeature() {
        assertFalse(ForgeFeature.testFeature(Dist.CLIENT, boundForMissingFeature));
    }

    @Test
    void testFeatureValueForMissingFeature() {
        assertEquals("NONE", ForgeFeature.featureValue(boundForMissingFeature));
    }

    @Test
    void testBoundValueForMissingFeature() {
        assertNull(boundForMissingFeature.bound());
    }

    @Test
    void testFeatureTestAlwaysSucceedsOnNonApplicableSides() {
        ForgeFeature.registerFeature("client_only_feature", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.CLIENT, "1.0"));

        // Sanity check
        var bound = new ForgeFeature.Bound("client_only_feature", "[5.0]", modInfo);
        assertFalse(ForgeFeature.testFeature(Dist.CLIENT, bound));

        // On server, the feature "doesn't apply", and checks against it always succeed.
        // Presumably to make mods that require OpenGL x.y.z on the client still load on servers.
        assertTrue(ForgeFeature.testFeature(Dist.DEDICATED_SERVER, bound));
    }

    @Nested
    class BooleanFeatureTest {
        @BeforeEach
        void setUp() {
            ForgeFeature.registerFeature("disabled_boolean_feature", new ForgeFeature.BooleanFeatureTest(IModInfo.DependencySide.BOTH, false));
            ForgeFeature.registerFeature("enabled_boolean_feature", new ForgeFeature.BooleanFeatureTest(IModInfo.DependencySide.BOTH, true));
        }

        @Test
        void testTestForEnabledFeature() {
            assertTrue(ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("enabled_boolean_feature", "true", modInfo)));
            assertFalse(ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("enabled_boolean_feature", "false", modInfo)));
        }

        @Test
        void testTestForDisabledFeature() {
            assertFalse(ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("disabled_boolean_feature", "true", modInfo)));
            assertTrue(ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("disabled_boolean_feature", "false", modInfo)));
        }

        @Test
        void testFeatureValue() {
            assertEquals("true", ForgeFeature.featureValue(new ForgeFeature.Bound("enabled_boolean_feature", "", modInfo)));
            assertEquals("false", ForgeFeature.featureValue(new ForgeFeature.Bound("disabled_boolean_feature", "", modInfo)));
        }

        @Test
        void testBound() {
            assertEquals(true, new ForgeFeature.Bound("enabled_boolean_feature", "TRUE", modInfo).bound());
            assertEquals(false, new ForgeFeature.Bound("enabled_boolean_feature", "FALSE", modInfo).bound());
        }
    }

    @Nested
    class VersionFeatureTest {
        @BeforeEach
        void setUp() {
            ForgeFeature.registerFeature("version_feature", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.BOTH, "5.0.0-beta"));
        }

        @Test
        void testMalformedBound() {
            var e = assertThrows(IllegalArgumentException.class, () -> ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("version_feature", "[,,,", modInfo)));
            assertThat(e).hasMessageContaining("Unbounded range: [,,,");
        }

        @Test
        void testWithinRange() {
            assertTrue(ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("version_feature", "[1.0,6.0]", modInfo)));
        }

        @Test
        void testOutSideOfRange() {
            assertFalse(ForgeFeature.testFeature(Dist.CLIENT, new ForgeFeature.Bound("version_feature", "[6.0,)", modInfo)));
        }

        @Test
        void testFeatureValue() {
            assertEquals("5.0.0-beta", ForgeFeature.featureValue(new ForgeFeature.Bound("version_feature", "", modInfo)));
        }

        @Test
        void testBound() throws Exception {
            var expected = VersionRange.createFromVersionSpec("[1.0,6.0]");
            assertEquals(expected, new ForgeFeature.Bound("version_feature", "[1.0,6.0]", modInfo).bound());
        }
    }
}
