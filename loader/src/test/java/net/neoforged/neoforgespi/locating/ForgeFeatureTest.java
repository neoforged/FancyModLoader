/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

class ForgeFeatureTest {
    @Nested
    class BooleanFeature {
        private static final String NAME = "booleantestfeature";

        @BeforeAll
        static void registerBooleanTestFeature() {
            ForgeFeature.registerFeature(NAME, new ForgeFeature.BooleanFeatureTest(
                    IModInfo.DependencySide.SERVER, true));
        }
    }

    @Nested
    class VersionRangeFeature {

    }
}
