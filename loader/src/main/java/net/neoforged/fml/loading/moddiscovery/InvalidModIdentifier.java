/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import cpw.mods.modlauncher.api.LambdaExceptionUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.zip.ZipFile;
import net.neoforged.fml.loading.StringUtils;

public enum InvalidModIdentifier {
    OLDFORGE(filePresent("mcmod.info")),
    FABRIC(filePresent("fabric.mod.json")),
    LITELOADER(filePresent("litemod.json")),
    OPTIFINE(filePresent("optifine/Installer.class")),
    BUKKIT(filePresent("plugin.yml")),
    INVALIDZIP((f, zf) -> !zf.isPresent());

    private BiPredicate<Path, Optional<ZipFile>> ident;

    InvalidModIdentifier(BiPredicate<Path, Optional<ZipFile>> identifier) {
        this.ident = identifier;
    }

    private String getReason() {
        return "fml.modloading.brokenfile." + StringUtils.toLowerCase(name());
    }

    public static Optional<String> identifyJarProblem(Path path) {
        Optional<ZipFile> zfo = tryOpenFile(path);
        Optional<String> result = Arrays.stream(values()).filter(i -> i.ident.test(path, zfo)).map(InvalidModIdentifier::getReason).findAny();
        zfo.ifPresent(LambdaExceptionUtils.rethrowConsumer(ZipFile::close));
        return result;
    }

    private static BiPredicate<Path, Optional<ZipFile>> filePresent(String filename) {
        return (f, zfo) -> zfo.map(zf -> zf.getEntry(filename) != null).orElse(false);
    }

    private static Optional<ZipFile> tryOpenFile(Path path) {
        try {
            return Optional.of(new ZipFile(path.toFile()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
