/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.neoforgespi.locating.IModLocator;

public abstract class AbstractJarFileModLocator extends AbstractJarFileModProvider implements IModLocator {
    @Override
    public List<IModLocator.ModFileOrException> scanMods() {
        return scanCandidates().map(this::createMod).toList();
    }

    public abstract Stream<Path> scanCandidates();

    protected static List<Path> getLegacyClasspath() {
        return Arrays.stream(System.getProperty("legacyClassPath", "").split(File.pathSeparator)).map(Path::of).toList();
    }
}
