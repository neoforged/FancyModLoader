/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Models the Maven coordinates for an artifact.
 */
public record MavenCoordinate(String groupId, String artifactId, String extension, String classifier, String version) {
    /**
     * Valid forms:
     * <ul>
     * <li>{@code groupId:artifactId:version}</li>
     * <li>{@code groupId:artifactId:version:classifier}</li>
     * <li>{@code groupId:artifactId:version:classifier@extension}</li>
     * <li>{@code groupId:artifactId:version@extension}</li>
     * </ul>
     */
    public static MavenCoordinate parse(String coordinate) {
        var parts = coordinate.split(":");

        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalArgumentException("Malformed Maven coordinate: " + coordinate);
        }

        var groupId = parts[0];
        var artifactId = parts[1];
        var classifier = parts.length == 3 ? parts[2] : "";
        var versext = parts[parts.length - 1].split("@");
        if (versext.length > 2) {
            throw new IllegalArgumentException("Malformed Maven coordinate: " + coordinate);
        }
        var version = versext[0];
        var extension = versext.length > 1 ? versext[1] : "";
        return new MavenCoordinate(groupId, artifactId, extension, classifier, version);
    }

    /**
     * Constructs a path relative to the root of a Maven repository pointing to the artifact expressed through
     * these coordinates.
     */
    public Path toRelativeRepositoryPath() {
        final String fileName = artifactId + "-" + version +
                (!classifier.isEmpty() ? "-" + classifier : "") +
                (!extension.isEmpty() ? "." + extension : ".jar");

        String[] groups = groupId.split("\\.");
        Path result = Paths.get(groups[0]);
        for (int i = 1; i < groups.length; i++) {
            result = result.resolve(groups[i]);
        }

        return result.resolve(artifactId).resolve(version).resolve(fileName);
    }
}
