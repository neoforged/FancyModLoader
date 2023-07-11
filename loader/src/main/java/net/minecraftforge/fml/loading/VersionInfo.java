/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading;

import java.util.Map;

public record VersionInfo(String forgeVersion, String fmlVersion, String mcVersion, String mcpVersion) {
    VersionInfo(Map<String, ?> arguments) {
        this((String) arguments.get("forgeVersion"), (String) arguments.get("fmlVersion"), (String) arguments.get("mcVersion"), (String) arguments.get("mcpVersion"));
    }

    public String mcAndForgeVersion() {
        return mcVersion + "-"+ forgeVersion;
    }
    public String mcAndFmlVersion() {
        return mcVersion+"-"+ fmlVersion;
    }

    public String mcAndMCPVersion() {
        return mcVersion + "-" + mcpVersion;
    }
}
