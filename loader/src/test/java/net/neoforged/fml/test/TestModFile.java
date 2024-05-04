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
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.nio.file.FileSystem;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.fml.loading.modscan.Scanner;
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
        this.compiler = new RuntimeCompiler(fileSystem);
    }

    private static TestModFile buildFile(FileSystem fileSystem, ModFileInfoParser parser) {
        var jc = new JarContentsBuilder()
                .paths(fileSystem.getPath("/"))
                .build();
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
        setScanResult(new Scanner(this).scan(), null);
    }

    @CheckReturnValue
    public static TestModFile newInstance(@Language("toml") String modsDotToml) {
        final var fs = Jimfs.newFileSystem(Configuration.unix()
                .toBuilder()
                .setWorkingDirectory("/")
                .build());
        var wrapper = new NightConfigWrapper(PARSER.parse(modsDotToml));
        return buildFile(fs, file -> new ModFileInfo((ModFile) file, wrapper, wrapper::setFile));
    }

    @Override
    public void close() throws IOException {
        getSecureJar().getRootPath().getFileSystem().close();
        fileSystem.close();
    }

    @Override
    public String getFileName() {
        return "dummy mod.jar";
    }
}
