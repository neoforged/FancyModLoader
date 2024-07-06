/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

public record VersionInfo(
        String neoForgeVersion,
        @Deprecated(forRemoval = true) String fmlVersion,
        String mcVersion,
        @Deprecated(forRemoval = true) String neoFormVersion) {
    public String mcAndFmlVersion() {
        return mcVersion + "-" + fmlVersion;
    }

    public String mcAndNeoFormVersion() {
        return mcVersion + "-" + neoFormVersion;
    }
}
