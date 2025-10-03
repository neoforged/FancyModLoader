/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.test;

import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import net.neoforged.fml.classloading.SecureJar;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.fml.testlib.RuntimeCompiler;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.ModFileInfoParser;
import org.intellij.lang.annotations.Language;

public class TestModFile extends ModFile implements AutoCloseable {
    private static final TomlParser PARSER = TomlFormat.instance().createParser();

    private final FileSystem fileSystem;
    private final RuntimeCompiler compiler;

    private TestModFile(SecureJar jar, FileSystem fileSystem, ModFileInfoParser parser) {
        super(jar, parser, new ModFileDiscoveryAttributes(null, null, null, null));
        this.fileSystem = fileSystem;
        this.compiler = RuntimeCompiler.createFolder(fileSystem.getPath("/"));
    }

    private static TestModFile buildFile(FileSystem fileSystem, ModFileInfoParser parser) throws IOException {
        var jc = JarContents.ofPath(fileSystem.getPath("/"));
        var metadata = new ModJarMetadata(jc);
        var sj = SecureJar.from(jc, metadata);
        var mod = new TestModFile(sj, fileSystem, parser);
        metadata.setModFile(mod);
        return mod;
    }

    @CheckReturnValue
    public RuntimeCompiler.CompilationBuilder classBuilder() {
        return compiler.builder();
    }

    public void scan() {
        startScan(Runnable::run);
    }

    @CheckReturnValue
    public static TestModFile newInstance(@Language("toml") String modsDotToml) throws IOException {
        final var fs = Jimfs.newFileSystem(Configuration.unix()
                .toBuilder()
                .setWorkingDirectory("/")
                .build());
        var wrapper = new NightConfigWrapper(PARSER.parse(modsDotToml));
        return buildFile(fs, file -> new ModFileInfo((ModFile) file, wrapper, wrapper::setFile));
    }

    @Override
    public void close() {
        super.close();
        try {
            fileSystem.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getFileName() {
        return "dummy mod.jar";
    }
}
