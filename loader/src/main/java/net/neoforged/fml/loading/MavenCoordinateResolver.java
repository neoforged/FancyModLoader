/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.nio.file.Path;

/**
 * Convert a maven coordinate into a Path.
 * <p>
 * This is gradle standard not maven standard coordinate formatting
 * {@code <groupId>:<artifactId>[:<classifier>]:<version>[@extension]}, must not be {@code null}.
 */

public class MavenCoordinateResolver {
    public static Path get(String coordinate) {
        return MavenCoordinate.parse(coordinate).toRelativeRepositoryPath();
    }

    public static Path get(String groupId, String artifactId, String extension, String classifier, String version) {
        return new MavenCoordinate(groupId, artifactId, extension, classifier, version).toRelativeRepositoryPath();
    }
}
