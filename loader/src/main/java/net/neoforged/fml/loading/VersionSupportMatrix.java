/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;
import net.neoforged.neoforgespi.language.MavenVersionAdapter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

public class VersionSupportMatrix {
    private static final HashMap<String, List<ArtifactVersion>> overrideVersions = new HashMap<>();
    static {
        final ArtifactVersion version = new DefaultArtifactVersion(FMLLoader.versionInfo().mcVersion());
        // If the MC version is 1.21.1 and any default version constraint fails,
        // we'll also pass the version check if the versions below match
        if (MavenVersionAdapter.createFromVersionSpec("[1.21.1]").containsVersion(version)) {
            add("mod.minecraft", "1.21");
            add("mod.neoforge", "21.0.166");
        }
    }

    private static void add(String key, String value) {
        overrideVersions.computeIfAbsent(key, k -> new ArrayList<>()).add(new DefaultArtifactVersion(value));
    }

    public static boolean testVersionSupportMatrix(VersionRange declaredRange, String lookupId, String type, BiPredicate<String, VersionRange> standardLookup) {
        if (standardLookup.test(lookupId, declaredRange)) {
            return true;
        }
        var custom = overrideVersions.get(type + "." + lookupId);
        return custom != null && custom.stream().anyMatch(declaredRange::containsVersion);
    }
}
