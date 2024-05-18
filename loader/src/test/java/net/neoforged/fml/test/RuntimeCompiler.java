/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.test;

import com.google.errorprone.annotations.CheckReturnValue;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeCompiler implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeCompiler.class);
    private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

    private final DiagnosticCollector<JavaFileObject> diagnostics;
    private final StandardJavaFileManager manager;
    private final Path rootPath;
    private FileSystem openedFileSystem;

    public static RuntimeCompiler create(Path targetFile) throws IOException {
        var fs = FileSystems.newFileSystem(URI.create("jar:" + targetFile.toUri()), Map.of("create", true));
        var result = new RuntimeCompiler(fs);
        result.openedFileSystem = fs;
        return result;
    }

    public RuntimeCompiler(FileSystem targetFS) {
        this.rootPath = targetFS.getPath("/");
        this.diagnostics = new DiagnosticCollector<>();
        this.manager = COMPILER.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        try {
            manager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(rootPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to set root output location", e);
        }
    }

    public CompilationBuilder builder() {
        return new CompilationBuilder();
    }

    public Path getRootPath() {
        return this.rootPath;
    }

    @Override
    public void close() throws IOException {
        manager.close();
        if (openedFileSystem != null) {
            openedFileSystem.close();
        }
    }

    public class CompilationBuilder {
        private final List<JavaFileObject> files = new ArrayList<>();

        @CheckReturnValue
        public CompilationBuilder addClass(String name, @Language("java") String content) {
            if (!content.trim().startsWith("package ")) {
                List<String> nameByDot = new ArrayList<>(Arrays.asList(name.split("\\.")));
                nameByDot.removeLast();

                content = ("package " + String.join(".", nameByDot) + ";\n" + content);
            }
            this.files.add(new JavaSourceFromString(name, content));
            return this;
        }

        public void compile() {
            if (files.isEmpty()) {
                return;
            }

            List<String> options = new ArrayList<>();
            options.add("-proc:none");

            var task = COMPILER.getTask(null, manager, diagnostics, options, null, files);
            if (!task.call()) {
                diagnostics.getDiagnostics().forEach(diagnostic -> LOGGER.error("Failed to compile: {}", diagnostic));
                throw new RuntimeException("Failed to compile class");
            }
        }
    }

    public static class JavaSourceFromString extends SimpleJavaFileObject {
        private final String sourceCode;

        public JavaSourceFromString(String name, String sourceCode) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }
}
