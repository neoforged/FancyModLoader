/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

public class LauncherVersion {
    private static final String launcherVersion;

    static {
        launcherVersion = JarVersionLookupHandler.getVersion(LauncherVersion.class).orElse("<unknown>");
    }

    public static String getVersion() {
        return launcherVersion;
    }
}
