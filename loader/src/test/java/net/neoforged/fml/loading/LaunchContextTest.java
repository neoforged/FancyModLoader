/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchContextTest {
    @TempDir
    Path tempDir;
    Path jarPath;
    Path otherJarPath;
    LaunchContext context;

    @BeforeEach
    void setUp() throws IOException {
        // Create a fake module layer with a jar on it.
        jarPath = tempDir.resolve("test.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            var je = new JarEntry("META-INF/MANIFEST.MF");
            out.putNextEntry(je);
            out.write("Manifest-Version: 1.0\nAutomatic-Module-Name: fancymodule\n".getBytes());
        }
        otherJarPath = tempDir.resolve("test-other-jar.jar");

        var cf = Configuration.resolveAndBind(
                ModuleFinder.of(),
                List.of(ModuleLayer.boot().configuration()),
                ModuleFinder.of(jarPath),
                List.of("fancymodule"));
        var moduleLayer = ModuleLayer.defineModulesWithOneLoader(
                cf,
                List.of(ModuleLayer.boot()),
                ClassLoader.getPlatformClassLoader()).layer();

        var environment = mock(IEnvironment.class);
        var moduleLayerManager = new IModuleLayerManager() {
            @Override
            public Optional<ModuleLayer> getLayer(Layer layer) {
                return layer == Layer.SERVICE ? Optional.of(moduleLayer) : Optional.empty();
            }
        };
        context = new LaunchContext(environment, moduleLayerManager, List.of(), List.of(), List.of());
    }

    @Test
    void testIsLocatedForJarOnLayer() {
        assertTrue(context.isLocated(jarPath));
    }

    @Test
    void testIsLocatedForJarNotOnLayer() {
        assertFalse(context.isLocated(otherJarPath));
    }

    @Test
    void testAddLocatedForJarOnLayer() {
        assertFalse(context.addLocated(jarPath));
    }

    @Test
    void testAddLocatedForJarNotOnLayer() {
        assertFalse(context.isLocated(otherJarPath));
        assertTrue(context.addLocated(otherJarPath));
        assertFalse(context.addLocated(otherJarPath));
        assertTrue(context.isLocated(otherJarPath));
    }
}
