/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;

public class FileUtils {
    public static String fileExtension(final Path path) {
        String fileName = path.getFileName().toString();
        int idx = fileName.lastIndexOf('.');
        if (idx > -1) {
            return fileName.substring(idx + 1);
        } else {
            return "";
        }
    }

    public static boolean matchFileName(String path, boolean exact, String... matches) {
        // Extract file name from path
        String name = Path.of(path).getFileName().toString();
        // Check if it contains any of the desired keywords
        for (String match : matches) {
            if (exact ? name.equals(match) : name.contains(match)) {
                return true;
            }
        }
        return false;
    }
}
