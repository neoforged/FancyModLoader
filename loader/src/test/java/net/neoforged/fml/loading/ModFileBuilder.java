/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.neoforged.fml.test.RuntimeCompiler;
import org.intellij.lang.annotations.Language;

public class ModFileBuilder implements Closeable {
    private final RuntimeCompiler compiler;
    private final FileSystem memoryFs;
    private final RuntimeCompiler.CompilationBuilder compilationBuilder;
    private final Path destination;
    private List<IdentifiableContent> content = new ArrayList<>();

    // Info that will end up in the mods.toml

    public ModFileBuilder(Path destination) throws IOException {
        this.destination = destination;
        memoryFs = Jimfs.newFileSystem(Configuration.unix());
        compiler = new RuntimeCompiler(memoryFs);
        compilationBuilder = compiler.builder();
    }

    public ModFileBuilder withModsToml(Consumer<ModsTomlBuilder> customizer) {
        var modsToml = new ModsTomlBuilder();
        customizer.accept(modsToml);
        content.add(modsToml.build());
        return this;
    }

    public ModFileBuilder addClass(String name, @Language("java") String content) {
        compilationBuilder.addClass(name, content);
        return this;
    }

    public void build() throws IOException {
        compilationBuilder.compile();

        var manifest = new Manifest();

        try (var output = new JarOutputStream(Files.newOutputStream(destination), manifest)) {
            // Copy compiled files over
            try (var files = Files.walk(memoryFs.getPath("/"))) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    var entry = new JarEntry(path.toString().replace('\\', '/'));
                    try {
                        output.putNextEntry(entry);
                        Files.copy(path, output);
                        output.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            for (var content : content) {
                var entry = new JarEntry(content.relativePath());
                try {
                    output.putNextEntry(entry);
                    output.write(content.content());
                    output.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        compiler.close();
    }
}
