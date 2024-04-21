/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.modlauncher.api.LambdaExceptionUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.StringUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.LoadResult;
import org.slf4j.Logger;

/**
 * Support loading mods located in JAR files in the mods folder
 */
public class ModsFolderLocator implements IModFileCandidateLocator {
    private static final String SUFFIX = ".jar";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path modFolder;
    private final String customName;

    public ModsFolderLocator() {
        this(FMLPaths.MODSDIR.get());
    }

    ModsFolderLocator(Path modFolder) {
        this(modFolder, "mods folder");
    }

    public ModsFolderLocator(Path modFolder, String name) {
        this.modFolder = Objects.requireNonNull(modFolder, "modFolder");
        this.customName = Objects.requireNonNull(name, "name");
    }

    @Override
    public Stream<LoadResult<JarContents>> findCandidates(ILaunchContext context) {
        LOGGER.debug(LogMarkers.SCAN, "Scanning mods dir {} for mods", this.modFolder);

        return LambdaExceptionUtils.uncheck(() -> Files.list(this.modFolder))
                .filter(p -> StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
                .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())))
                .map(IModFileCandidateLocator::result);
    }

    @Override
    public String name() {
        return customName;
    }

    @Override
    public String toString() {
        return "{" + customName + " locator at " + this.modFolder + "}";
    }
}
