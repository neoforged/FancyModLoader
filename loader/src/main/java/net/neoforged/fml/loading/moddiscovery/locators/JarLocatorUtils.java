/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class JarLocatorUtils {
    private JarLocatorUtils() {}

    static List<Path> getLegacyClasspath() {
        return Arrays.stream(System.getProperty("legacyClassPath", "").split(File.pathSeparator)).map(Path::of).toList();
    }
}
