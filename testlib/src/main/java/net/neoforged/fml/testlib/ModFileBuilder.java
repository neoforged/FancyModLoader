/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.intellij.lang.annotations.Language;

public abstract class ModFileBuilder<T extends ModFileBuilder<T>> {
    public static final ContainedVersion JIJ_V1 = new ContainedVersion(VersionRange.createFromVersion("1.0"), new DefaultArtifactVersion("1.0"));

    protected final RuntimeCompiler compiler;
    protected final FileSystem memoryFs;
    protected final Path memoryFsRoot;
    protected final RuntimeCompiler.CompilationBuilder compilationBuilder;
    protected final List<IdentifiableContent> content = new ArrayList<>();
    protected final Manifest manifest = new Manifest();
    protected final List<ContainedJarMetadata> jijEntries = new ArrayList<>();

    protected ModFileBuilder() {
        memoryFs = Jimfs.newFileSystem(Configuration.unix());
        compiler = RuntimeCompiler.createFolder(memoryFs.getRootDirectories().iterator().next());
        compilationBuilder = compiler.builder();
        memoryFsRoot = memoryFs.getRootDirectories().iterator().next();

        // Add the current classpath as the compile classpath
        Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .forEach(compilationBuilder::addClasspath);
    }

    public static ModJarBuilder toJar(Path destination) {
        return new ModJarBuilder(destination);
    }

    public static ModFoldersBuilder toGradleOutputFolders(Path classesDestination, Path resourcesDestination) {
        return new ModFoldersBuilder(classesDestination, resourcesDestination);
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    public T withTestmodModsToml() {
        return withTestmodModsToml(ignored -> {});
    }

    public T withTestmodModsToml(Consumer<ModsTomlBuilder> customizer) {
        return withModsToml(builder -> {
            builder.unlicensedJavaMod();
            builder.addMod("testmod", "1.0");
            customizer.accept(builder);
        });
    }

    public T withMod(String id, String version) {
        return withModsToml(builder -> builder.unlicensedJavaMod().addMod(id, version));
    }

    public T withModTypeManifest(String type) {
        return withManifest(Map.of("FMLModType", type));
    }

    public T withModuleInfo(ModuleDescriptor descriptor) throws IOException {
        return addBinaryFile("module-info.class", ModuleInfoWriter.toByteArray(descriptor));
    }

    public T withManifest(Map<String, String> manifest) {
        this.manifest.clear();
        for (var entry : manifest.entrySet()) {
            this.manifest.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }
        return self();
    }

    public T withModsToml(Consumer<ModsTomlBuilder> customizer) {
        var modsToml = new ModsTomlBuilder();
        customizer.accept(modsToml);
        content.add(modsToml.build());
        return self();
    }

    public T addService(String interfaceClass, String implementationClass) throws IOException {
        var serviceFile = memoryFsRoot.resolve("META-INF/services/" + interfaceClass);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, implementationClass + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return self();
    }

    public T addService(Class<?> interfaceClass, String implementationClass) throws IOException {
        return addService(interfaceClass.getName(), implementationClass);
    }

    public T addService(Class<?> interfaceClass, Class<?> implementationClass) throws IOException {
        return addService(interfaceClass.getName(), implementationClass.getName());
    }

    public T addCompileClasspath(Path jar) {
        compilationBuilder.addClasspath(jar);
        return self();
    }

    public T addModulePath(Path jar) {
        compilationBuilder.addModulePath(jar);
        return self();
    }

    public T addClass(String name, @Language("java") String content) {
        compilationBuilder.addClass(name, content);
        return self();
    }

    public T addTextFile(String path, String content) throws IOException {
        var p = memoryFsRoot.resolve(path);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return self();
    }

    public T addBinaryFile(String path, byte[] content) throws IOException {
        var p = memoryFsRoot.resolve(path);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.write(p, content);
        return self();
    }

    @FunctionalInterface
    public interface ModJarCustomizer {
        void customize(ModFileBuilder<?> builder) throws IOException;
    }

    public T withJarInJar(ContainedJarIdentifier identifier, ModJarCustomizer childModCustomizer) throws Exception {
        return withJarInJar(identifier, JIJ_V1, childModCustomizer);
    }

    public T withJarInJar(ContainedJarIdentifier identifier, ContainedVersion version, ModJarCustomizer childModCustomizer) throws Exception {
        var filename = identifier.artifact() + "-" + version.artifactVersion().toString() + ".jar";
        var relativePath = "META-INF/jarjar/" + filename;

        var tempPath = Files.createTempFile("jijfile", ".jar");
        try {
            var childBuilder = ModFileBuilder.toJar(tempPath);
            childModCustomizer.customize(childBuilder);
            childBuilder.build();

            var rootPath = memoryFs.getRootDirectories().iterator().next();
            var embeddedPath = rootPath.resolve(relativePath);
            Files.createDirectories(embeddedPath.getParent());
            Files.copy(tempPath, embeddedPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }

        jijEntries.add(new ContainedJarMetadata(identifier, version, relativePath, false));
        return self();
    }

    protected void buildInternal() {
        compilationBuilder.compile();

        if (!jijEntries.isEmpty()) {
            content.add(SimulatedInstallation.createJijMetadata(jijEntries.toArray(ContainedJarMetadata[]::new)));
        }

        // Without a manifest version, the entire manifest is ignored
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    }

    public static class ModFoldersBuilder extends ModFileBuilder<ModFoldersBuilder> {
        private final Path classesDestination;
        private final Path resourcesDestination;

        public ModFoldersBuilder(Path classesDestination, Path resourcesDestination) {
            this.classesDestination = Objects.requireNonNull(classesDestination);
            this.resourcesDestination = Objects.requireNonNullElse(resourcesDestination, classesDestination);
        }

        private OutputStream bufferedOut(Path destination) throws IOException {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            return new BufferedOutputStream(Files.newOutputStream(destination));
        }

        public List<Path> build() throws IOException {
            buildInternal();

            try (var output = bufferedOut(resourcesDestination.resolve("META-INF/MANIFEST.MF"))) {
                manifest.write(output);
            }

            // Copy compiled files over
            try (var files = Files.walk(memoryFs.getPath("/"))) {
                var it = files.iterator();
                while (it.hasNext()) {
                    var path = it.next();
                    var relativePath = path.toString().replace('\\', '/');
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                    Path destinationFile = classesDestination.resolve(relativePath);

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destinationFile);
                    } else {
                        Files.copy(path, destinationFile);
                    }
                }
            }

            for (var content : content) {
                Path destinationFolder;
                if (content.relativePath().endsWith(".class")) {
                    destinationFolder = classesDestination;
                } else {
                    destinationFolder = resourcesDestination;
                }

                var destination = destinationFolder.resolve(content.relativePath());
                if (destination.getParent() != null) {
                    Files.createDirectories(destination.getParent());
                }
                Files.write(destination, content.content());
            }

            close();

            if (resourcesDestination != classesDestination) {
                return List.of(classesDestination, resourcesDestination);
            } else {
                return List.of(classesDestination);
            }
        }
    }

    public static class ModJarBuilder extends ModFileBuilder<ModJarBuilder> {
        private final Path destination;

        public ModJarBuilder(Path destination) {
            this.destination = destination;
        }

        public Path build() throws IOException {
            this.buildInternal();

            try (var output = new JarOutputStream(Files.newOutputStream(destination), manifest)) {
                // Copy compiled files over
                try (var files = Files.walk(memoryFs.getPath("/"))) {
                    files.filter(Files::isRegularFile).forEach(path -> {
                        var relativePath = path.toString().replace('\\', '/');
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }
                        var entry = new JarEntry(relativePath);
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

            close();

            return destination;
        }
    }

    public void close() throws IOException {
        compiler.close();
    }
}
