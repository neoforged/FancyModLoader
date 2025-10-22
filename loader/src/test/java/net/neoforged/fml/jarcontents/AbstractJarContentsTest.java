/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.jarcontents;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractJarContentsTest {
    @TempDir
    Path tempDir;

    private int tempJarCounter;

    Path writeTextFile(String relativePath, String content) throws IOException {
        var path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    Path makeJar() throws IOException {
        return makeJar(ignored -> {});
    }

    Path makeJar(String subfolder) throws IOException {
        return makeJar(subfolder, ignored -> {});
    }

    Path makeJar(Consumer<Manifest> manifestConsumer) throws IOException {
        return makeJar(null, manifestConsumer);
    }

    Path makeJar(String subfolder, Consumer<Manifest> manifestConsumer) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifestConsumer.accept(mf);

        var root = subfolder != null ? tempDir.resolve(subfolder) : tempDir;

        var tempJar = tempDir.resolve("_tempjar" + (++tempJarCounter) + ".jar");
        try (var jarOut = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(tempJar)), mf)) {
            try (var stream = Files.walk(root)) {
                for (var it = stream.iterator(); it.hasNext();) {
                    var path = it.next();
                    if (path.getFileName().toString().startsWith("_tempjar") || path.equals(root)) {
                        continue;
                    }
                    var relativePath = root.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path)) {
                        JarEntry entry = new JarEntry(relativePath + "/");
                        jarOut.putNextEntry(entry);
                    } else {
                        JarEntry entry = new JarEntry(relativePath);
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        entry.setLastModifiedTime(attrs.lastModifiedTime());
                        entry.setCreationTime(attrs.creationTime());
                        jarOut.putNextEntry(entry);
                        jarOut.write(Files.readAllBytes(path));
                    }
                    jarOut.closeEntry();
                }
            }
        }
        return tempJar;
    }
}
