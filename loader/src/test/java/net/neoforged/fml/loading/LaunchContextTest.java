/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cpw.mods.modlauncher.api.IEnvironment;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.test.RuntimeCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LaunchContextTest {
    private static final Logger log = LoggerFactory.getLogger(LaunchContextTest.class);
    @TempDir
    Path tempDir;
    Path jarPath;
    Path otherJarPath;
    LaunchContext context;
    private URLClassLoader serviceLoader;
    private ModuleLayer pluginLayer;
    private ClassLoader pluginLoader;
    private ClassLoader previousClassLoader;

    @BeforeEach
    void setUp() throws IOException {
        previousClassLoader = Thread.currentThread().getContextClassLoader();

        // Create a fake module layer with a jar on it.
        jarPath = tempDir.resolve("test.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            var je = new JarEntry("META-INF/MANIFEST.MF");
            out.putNextEntry(je);
            out.write("Manifest-Version: 1.0\nAutomatic-Module-Name: fancymodule\n".getBytes());
        }

        try (var compiler = RuntimeCompiler.create(jarPath)) {
            compiler.builder()
                    .addClass("pkg.TestService", """
                            package pkg;
                            public class TestService implements Runnable {
                                public void run() {}
                            }""")
                    .compile();
            var serviceFile = compiler.getRootPath().resolve("META-INF/services/java.lang.Runnable");
            Files.createDirectories(serviceFile.getParent());
            Files.writeString(serviceFile, "pkg.TestService");
        }

        // Create a second jar file
        otherJarPath = tempDir.resolve("test-other-jar.jar");
        try (var compiler = RuntimeCompiler.create(otherJarPath)) {
            compiler.builder()
                    .addClass("pkg2.TestService", """
                            package pkg2;
                            public class TestService implements Runnable {
                                public void run() {}
                            }""")
                    .compile();
            var serviceFile = compiler.getRootPath().resolve("META-INF/services/java.lang.Runnable");
            Files.createDirectories(serviceFile.getParent());
            Files.writeString(serviceFile, "pkg2.TestService");
        }

        // Emulates services being on the classpath
        serviceLoader = new URLClassLoader(new URL[] { jarPath.toUri().toURL() });
        Thread.currentThread().setContextClassLoader(serviceLoader);

        var environment = mock(IEnvironment.class);
        context = new LaunchContext(environment, Dist.CLIENT, tempDir, List.of(), List.of(), List.of(), List.of());

        // Create the plugin-layer after the ctor has already been called
        pluginLayer = createModuleLayer(otherJarPath, "test.other.jar", serviceLoader);
        pluginLoader = pluginLayer.findLoader(pluginLayer.modules().iterator().next().getName());
        Thread.currentThread().setContextClassLoader(pluginLoader);
    }

    private ModuleLayer createModuleLayer(Path path, String moduleName, ClassLoader parentLoader) {
        var cf = Configuration.resolveAndBind(
                ModuleFinder.of(),
                List.of(ModuleLayer.boot().configuration()),
                ModuleFinder.of(path),
                List.of(moduleName));
        return ModuleLayer.defineModulesWithOneLoader(
                cf,
                List.of(ModuleLayer.boot()),
                parentLoader).layer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (serviceLoader != null) {
            serviceLoader.close();
        }
        Thread.currentThread().setContextClassLoader(previousClassLoader);

        // Desperate attempts at clearing up the lock on the jar file
        context = null;
        pluginLayer = null;
        pluginLoader = null;
        System.gc();
    }

    @Test
    void testLoadServices() {
        // Null the plugin layer temporarily
        Thread.currentThread().setContextClassLoader(serviceLoader);

        var serviceLayerOnlyServices = context.loadServices(Runnable.class).toList();
        assertThat(serviceLayerOnlyServices)
                .map(ServiceLoader.Provider::type)
                .map(Class::getName)
                .containsOnly("pkg.TestService");

        // Now restore it and observe that services from both layers are returned
        Thread.currentThread().setContextClassLoader(pluginLoader);
        var serviceAndPluginLayerServices = context.loadServices(Runnable.class).toList();
        assertThat(serviceAndPluginLayerServices)
                .map(ServiceLoader.Provider::type)
                .map(Class::getName)
                .containsOnly("pkg2.TestService", "pkg.TestService");
    }

    @Test
    void testIsLocatedForJarNotOnLayer() {
        assertFalse(context.isLocated(otherJarPath));
    }

    @Test
    void testAddLocatedForJarNotOnLayer() {
        assertFalse(context.isLocated(otherJarPath));
        assertTrue(context.addLocated(otherJarPath));
        assertFalse(context.addLocated(otherJarPath));
        assertTrue(context.isLocated(otherJarPath));
    }
}
