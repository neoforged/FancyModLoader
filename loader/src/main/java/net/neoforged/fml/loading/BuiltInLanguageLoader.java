/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import net.neoforged.neoforgespi.language.IModLanguageLoader;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarInputStream;

public abstract class BuiltInLanguageLoader implements IModLanguageLoader {
    @Override
    public String version() {
        return JarVersionLookupHandler.getVersion(this.getClass())
                .orElseGet(() -> {
                    final Path lpPath;
                    try {
                        lpPath = Paths.get(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException("Huh?", e);
                    }

                    if (Files.isDirectory(lpPath)) {
                        return FMLLoader.versionInfo().fmlVersion();
                    }

                    try (var jin = new JarInputStream(new FileInputStream(lpPath.toFile()))) {
                        var manifest = jin.getManifest();
                        if (manifest != null) {
                            return manifest.getMainAttributes().getValue("Implementation-Version"); // May be null
                        }
                    } catch (IOException ignored) {
                    }

                    return null;
                });
    }
}
