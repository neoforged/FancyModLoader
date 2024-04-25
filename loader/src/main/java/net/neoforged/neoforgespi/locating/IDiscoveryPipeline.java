/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import cpw.mods.jarhandling.JarContents;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.neoforged.fml.ModLoadingIssue;
import org.jetbrains.annotations.Nullable;

public interface IDiscoveryPipeline {
    default Optional<IModFile> addPath(Path path, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting incompatibleFileReporting) {
        return addPath(List.of(path), attributes, incompatibleFileReporting);
    }

    Optional<IModFile> addPath(List<Path> groupedPaths, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting incompatibleFileReporting);

    Optional<IModFile> addJarContent(JarContents contents, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting incompatibleFileReporting);

    boolean addModFile(IModFile modFile);

    void addIssue(ModLoadingIssue issue);

    @Nullable
    IModFile readModFile(JarContents jarContents, ModFileDiscoveryAttributes attributes);
}
