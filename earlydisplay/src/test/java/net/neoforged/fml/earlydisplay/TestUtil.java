/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestUtil {
    static Path findProjectRoot() throws Exception {
        // Find the project directory by search for build.gradle upwards
        return findProjectRoot(Paths.get(TestUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }

    static Path findProjectRoot(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalArgumentException("Couldn't find buid.gradle in any parent directory of " + path);
    }
}
