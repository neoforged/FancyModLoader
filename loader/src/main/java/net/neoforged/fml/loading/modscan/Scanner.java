/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.modscan;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Scanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ModFile fileToScan;

    public Scanner(final ModFile fileToScan) {
        this.fileToScan = fileToScan;
    }

    public ModFileScanData scanCached() {
        var key = fileToScan.getCacheKey();
        if (key == null) return scan();
        var path = FMLPaths.CACHEDIR.resolve(key);
        try (var in = new ObjectInputStream(Files.newInputStream(path))) {
            var scan = ModFileScanData.read(in);
            LOGGER.debug("Reading scan data for file {} from cache at {}", fileToScan, path);
            if (scan != null) {
                scan.addModFileInfo(fileToScan.getModFileInfo());
                return scan;
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to read mod file scan data for file {} from cache at {}", fileToScan, path);
        }
        var computed = scan();
        try (var out = new ObjectOutputStream(Files.newOutputStream(path))) {
            computed.write(out);
        } catch (Exception exception) {
            LOGGER.error("Failed to write mod file scan data for file {} to cache at {}", fileToScan, path);
        }
        return computed;
    }

    public ModFileScanData scan() {
        ModFileScanData result = new ModFileScanData();
        result.addModFileInfo(fileToScan.getModFileInfo());
        fileToScan.scanFile(p -> fileVisitor(p, result));
        return result;
    }

    private void fileVisitor(final Path path, final ModFileScanData result) {
        try (InputStream in = Files.newInputStream(path)) {
            ModClassVisitor mcv = new ModClassVisitor();
            ClassReader cr = new ClassReader(in);
            cr.accept(mcv, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            mcv.buildData(result.getClasses(), result.getAnnotations());
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error(LogMarkers.SCAN, "Exception scanning {} path {}", fileToScan, path, e);
        }
    }
}
