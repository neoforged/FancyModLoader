/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import net.neoforged.fml.test.TestModFile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

public class ModInfoTest {
    /**
     * Regression test to ensure that unbounded ranges are actually unbounded.
     * See <a href="https://github.com/neoforged/FancyModLoader/issues/34">issue</a>.
     */
    @Test
    public void testUnboundedRange() {
        assertTrue(IModInfo.UNBOUNDED.containsVersion(new DefaultArtifactVersion("0.0.1")));
        assertTrue(IModInfo.UNBOUNDED.containsVersion(new DefaultArtifactVersion("1.0.0")));
        assertTrue(IModInfo.UNBOUNDED.containsVersion(new DefaultArtifactVersion("10000.0.0")));
    }

    @Test
    public void testDonationUrl() throws Exception {
        try (var modFile = TestModFile.newInstance("""
                license="LGPL v3"

                [[mods]]
                modId="testmod"
                version="1.0"
                donationURL="https://ko-fi.com/testmod"
                """)) {
            assertThat(modFile.getModInfos().getFirst().getDonationURL())
                    .contains(URI.create("https://ko-fi.com/testmod").toURL());
        }
    }

    @Test
    public void testMissingDonationUrl() throws Exception {
        try (var modFile = TestModFile.newInstance("""
                license="LGPL v3"

                [[mods]]
                modId="testmod"
                version="1.0"
                """)) {
            assertThat(modFile.getModInfos().getFirst().getDonationURL()).isEmpty();
        }
    }

    @Test
    public void testPlaceholderDonationUrl() throws Exception {
        try (var modFile = TestModFile.newInstance("""
                license="LGPL v3"

                [[mods]]
                modId="testmod"
                version="1.0"
                donationURL="https://change.me.example.invalid/"
                """)) {
            assertThat(modFile.getModInfos().getFirst().getDonationURL()).isEmpty();
        }
    }
}
