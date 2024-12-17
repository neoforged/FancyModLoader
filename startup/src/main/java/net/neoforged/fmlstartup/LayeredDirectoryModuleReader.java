/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class LayeredDirectoryModuleReader implements ModuleReader {
    private final List<File> directories;

    public LayeredDirectoryModuleReader(List<File> directories) {
        this.directories = directories;
    }

    @Override
    public Optional<URI> find(String name) throws IOException {
        if (name.contains("\\")) {
            // We do not want to deal with that. The spec says use forward slashes.
            return Optional.empty();
        }
        if (name.isEmpty()) {
            return Optional.empty();
        }

        boolean expectDir = false;

        // Validate segments
        var startOfSegment = 0;
        for (var endOfSegment = getEndOfPathSegment(name, 0); startOfSegment <= name.length(); endOfSegment = getEndOfPathSegment(name, startOfSegment)) {
            var segmentLength = endOfSegment - startOfSegment;
            if (segmentLength == 0) {
                // The last segment may be empty and indicates the caller is looking for a directory
                if (endOfSegment == name.length()) {
                    expectDir = true;
                } else {
                    return Optional.empty(); // No empty segments
                }
            } else if (segmentLength == 1 && name.charAt(startOfSegment) == '.') {
                return Optional.empty(); // No '.' in segments
            } else if (segmentLength == 2 && name.charAt(startOfSegment) == '.' && name.charAt(startOfSegment + 1) == '.') {
                return Optional.empty(); // No '..' in segments
            }
            startOfSegment = endOfSegment + 1;
        }

        for (var directory : directories) {
            var f = new File(directory, name);
            var isDirectory = f.isDirectory();
            if (expectDir && !isDirectory) {
                continue;
            }
            if (f.exists()) {
                return Optional.of(f.toURI());
            }
        }

        return Optional.empty();
    }

    private static int getEndOfPathSegment(String name, int startAt) {
        var idx = name.indexOf('/', startAt);
        if (idx == -1) {
            idx = name.length();
        }
        return idx;
    }

    @Override
    public Stream<String> list() throws IOException {
        // This is not super optimized as it should not be called
        var result = Stream.<String>empty();

        for (File directory : directories) {
            var dirPath = directory.toPath();
            result = Stream.concat(result, Files.walk(dirPath, Integer.MAX_VALUE)
                    .map(f -> toResourceName(dirPath, f))
                    .filter(s -> !s.isEmpty()));
        }

        return result;
    }

    public static String toResourceName(Path dir, Path file) {
        String s = dir.relativize(file)
                .toString()
                .replace('\\', '/');
        if (!s.isEmpty() && Files.isDirectory(file)) {
            s += "/";
        }
        return s;
    }

    @Override
    public void close() {}
}
