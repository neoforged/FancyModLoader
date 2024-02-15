/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;

public class Scanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ModFile fileToScan;

    public Scanner(final ModFile fileToScan) {
        this.fileToScan = fileToScan;
    }

    public ModFileScanData scan() {
        ModFileScanData result = new ModFileScanData();
        result.addModFileInfo(fileToScan.getModFileInfo());
        fileToScan.scanFile(p -> fileVisitor(p, result));
        final List<IModLanguageProvider> loaders = fileToScan.getLoaders();
        if (loaders != null) {
            loaders.forEach(loader -> {
                LOGGER.debug(LogMarkers.SCAN, "Scanning {} with language loader {}", fileToScan.getFilePath(), loader.name());
                loader.getFileVisitor().accept(result);
            });
        }
        return result;
    }

    private void fileVisitor(final Path path, final ModFileScanData result) {
        LOGGER.debug(LogMarkers.SCAN, "Scanning {} path {}", fileToScan, path);
        try (InputStream in = Files.newInputStream(path)) {
            ModClassVisitor mcv = new ModClassVisitor();
            ClassReader cr = new ClassReader(in);
            cr.accept(mcv, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            mcv.buildData(result.getClasses(), result.getAnnotations());
        } catch (IOException | IllegalArgumentException e) {
            // mark path bad
        }
    }
}
