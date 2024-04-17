/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.util.Map;

public record VersionInfo(String neoForgeVersion, String fmlVersion, String mcVersion, String neoFormVersion, String neoForgeGroup) {
    VersionInfo(Map<String, ?> arguments) {
        this((String) arguments.get("neoForgeVersion"), (String) arguments.get("fmlVersion"), (String) arguments.get("mcVersion"), (String) arguments.get("neoFormVersion"), getNeoForgeGroup((String) arguments.get("neoForgeGroup")));
    }

    public String mcAndFmlVersion() {
        return mcVersion+"-"+ fmlVersion;
    }

    public String mcAndNeoFormVersion() {
        return mcVersion + "-" + neoFormVersion;
    }
    
    private static String getNeoForgeGroup(String neoForgeGroup) {
        return neoForgeGroup == null ? "net.neoforged" : neoForgeGroup;
    }
}
