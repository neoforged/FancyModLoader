/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.testlib.ModFileBuilder;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransformerDiscovererConstantsTest {
    @TempDir
    Path tempDir;

    Path testJar;

    @Test
    void testEmptyJar() throws IOException {
        assertFalse(shouldLoad(builder -> {}));
    }

    @Test
    void testAutomaticModuleWithMatchingService() throws IOException {
        assertTrue(shouldLoad(builder -> {
            builder.addBinaryFile("pkg.Impl", new byte[0]);
            builder.addService("net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper", "pkg.Impl");
        }));
    }

    @Test
    void testModularJarWithMatchingService() throws IOException {
        assertTrue(shouldLoad(builder -> {
            builder.withModuleInfo(ModuleDescriptor.newModule("some_module")
                    .provides(GraphicsBootstrapper.class.getName(), List.of("pkg.Impl"))
                    .build());
        }));
    }

    @Test
    void testBrokenModuleInfoThrowsDescriptiveError() {
        var e = assertThrows(Exception.class, () -> shouldLoad(builder -> {
            builder.addBinaryFile("module-info.class", new byte[] {});
        }));
        assertThat(e).hasMessageContaining("Invalid module-info.class in " + testJar);
    }

    @Test
    void testDirectoryInPlaceOfServiceFileIsIgnored() throws IOException {
        assertFalse(shouldLoad(builder -> {
            // This creates the file as a directory, which will fail to open as a stream
            builder.addTextFile("net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper/dummy", "");
        }));
    }

    private boolean shouldLoad(ModFileBuilder.ModJarCustomizer customizer) throws IOException {
        testJar = tempDir.resolve("test.jar");
        Files.createDirectories(testJar.getParent());

        var builder = new ModFileBuilder(testJar);
        try {
            customizer.customize(builder);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        builder.build();

        return TransformerDiscovererConstants.shouldLoadInServiceLayer(testJar);
    }
}
