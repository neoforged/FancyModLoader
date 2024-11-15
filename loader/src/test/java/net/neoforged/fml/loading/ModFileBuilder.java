/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.neoforged.fml.test.RuntimeCompiler;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.intellij.lang.annotations.Language;

public class ModFileBuilder {
    public static final ContainedVersion JIJ_V1 = new ContainedVersion(VersionRange.createFromVersion("1.0"), new DefaultArtifactVersion("1.0"));

    private final RuntimeCompiler compiler;
    private final FileSystem memoryFs;
    private final Path memoryFsRoot;
    private final RuntimeCompiler.CompilationBuilder compilationBuilder;
    private final Path destination;
    private final List<IdentifiableContent> content = new ArrayList<>();
    private final Manifest manifest = new Manifest();
    private final List<ContainedJarMetadata> jijEntries = new ArrayList<>();

    // Info that will end up in the mods.toml

    public ModFileBuilder(Path destination) throws IOException {
        this.destination = destination;
        memoryFs = Jimfs.newFileSystem(Configuration.unix());
        compiler = new RuntimeCompiler(memoryFs);
        compilationBuilder = compiler.builder();
        memoryFsRoot = memoryFs.getRootDirectories().iterator().next();
    }

    public ModFileBuilder withTestmodModsToml() {
        return withTestmodModsToml(ignored -> {});
    }

    public ModFileBuilder withTestmodModsToml(Consumer<ModsTomlBuilder> customizer) {
        return withModsToml(builder -> {
            builder.unlicensedJavaMod();
            builder.addMod("testmod", "1.0");
            customizer.accept(builder);
        });
    }

    public ModFileBuilder withMod(String id, String version) {
        return withModsToml(builder -> builder.unlicensedJavaMod().addMod(id, version));
    }

    public ModFileBuilder withModTypeManifest(IModFile.Type type) {
        return withManifest(Map.of(
                "FMLModType", type.name()));
    }

    public ModFileBuilder withManifest(Map<String, String> manifest) {
        this.manifest.clear();
        for (var entry : manifest.entrySet()) {
            this.manifest.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public ModFileBuilder withModsToml(Consumer<ModsTomlBuilder> customizer) {
        var modsToml = new ModsTomlBuilder();
        customizer.accept(modsToml);
        content.add(modsToml.build());
        return this;
    }

    public ModFileBuilder addService(String interfaceClass, String implementationClass) throws IOException {
        var serviceFile = memoryFsRoot.resolve("META-INF/services/" + interfaceClass);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, implementationClass + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return this;
    }

    public ModFileBuilder addService(Class<?> interfaceClass, String implementationClass) throws IOException {
        return addService(interfaceClass.getName(), implementationClass);
    }

    public ModFileBuilder addService(Class<?> interfaceClass, Class<?> implementationClass) throws IOException {
        return addService(interfaceClass.getName(), implementationClass.getName());
    }

    public ModFileBuilder addClass(String name, @Language("java") String content) {
        compilationBuilder.addClass(name, content);
        return this;
    }

    public ModFileBuilder addTextFile(String path, String content) throws IOException {
        var p = memoryFsRoot.resolve(path);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return this;
    }

    @FunctionalInterface
    public interface ModJarCustomizer {
        void customize(ModFileBuilder builder) throws IOException;
    }

    public ModFileBuilder withJarInJar(ContainedJarIdentifier identifier, ModJarCustomizer childModCustomizer) throws Exception {
        return withJarInJar(identifier, JIJ_V1, childModCustomizer);
    }

    public ModFileBuilder withJarInJar(ContainedJarIdentifier identifier, ContainedVersion version, ModJarCustomizer childModCustomizer) throws Exception {
        var filename = identifier.artifact() + "-" + version.artifactVersion().toString() + ".jar";
        var relativePath = "META-INF/jarjar/" + filename;

        var tempPath = Files.createTempFile("jijfile", ".jar");
        try {
            var childBuilder = new ModFileBuilder(tempPath);
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
        return this;
    }

    public Path build() throws IOException {
        compilationBuilder.compile();

        if (!jijEntries.isEmpty()) {
            content.add(SimulatedInstallation.createJijMetadata(jijEntries.toArray(ContainedJarMetadata[]::new)));
        }

        // Without a manifest version, the entire manifest is ignored
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

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

    public void close() throws IOException {
        compiler.close();
    }
}
