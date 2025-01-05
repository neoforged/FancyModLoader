/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import net.neoforged.neoforgespi.locating.IModFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the interaction between a plugin/library that consumes a service via ServiceLoader
 * and a mod that implements said service.
 * We expect the plugin to be able to find the game class that implements the service.
 */
class PluginAndModServiceLoaderInteractionTest extends LauncherTest {
    Path modJar;
    Path pluginJar;

    @BeforeEach
    void setupJars() throws IOException {
        pluginJar = installation.buildModJar("../plugin.jar")
                .withModTypeManifest(IModFile.Type.LIBRARY)
                .addClass("plugin.ServiceInterface", "public interface ServiceInterface {}")
                .addClass("plugin.ServiceConsumer", """
                        import java.util.List;
                        import java.util.ServiceLoader;
                        public class ServiceConsumer {
                            public static List<Class<? extends plugin.ServiceInterface>> test() {
                                return ServiceLoader.load(ServiceInterface.class).stream().map(ServiceLoader.Provider::type).toList();
                            }
                        }""")
                .build();

        modJar = installation.buildModJar("../mod.jar")
                .withTestmodModsToml()
                .addCompilationClasspath(pluginJar)
                .addClass("mod.ServiceImplementation", """
                        public class ServiceImplementation implements plugin.ServiceInterface {}
                        """)
                .addService("plugin.ServiceInterface", "mod.ServiceImplementation")
                .build();
    }

    @Test
    void inProduction() throws Exception {
        installation.setupProductionClient();

        // Copy them into the mods folder
        Files.copy(modJar, installation.getModsFolder().resolve(modJar.getFileName()));
        Files.copy(pluginJar, installation.getModsFolder().resolve(pluginJar.getFileName()));

        var launchResult = launchAndLoad(LauncherTest.LaunchMode.PROD_CLIENT);

        assertServiceLoaderInteraction(launchResult);
    }

    @Test
    void inDevelopment() throws Exception {
        var classpath = installation.setupUserdevProject();
        Collections.addAll(classpath, modJar, pluginJar);

        var launchResult = launchAndLoadWithAdditionalClasspath(LauncherTest.LaunchMode.DEV_CLIENT, classpath);

        assertServiceLoaderInteraction(launchResult);
    }

    @SuppressWarnings("unchecked")
    private void assertServiceLoaderInteraction(LaunchResult launchResult) throws Exception {
        // Get the test class from the plugin and ensure it's not on the TCL
        var serviceConsumerClass = Class.forName("plugin.ServiceConsumer", true, launchResult.launchClassLoader());
        assertNotEquals(serviceConsumerClass.getClassLoader(), launchResult.launchClassLoader(), "Plugin class should not be loaded on the transforming class loader");

        // Get the mod implementation class end ensure it is on the TCL
        var expectedImpl = Class.forName("mod.ServiceImplementation", true, launchResult.launchClassLoader());
        assertEquals(expectedImpl.getClassLoader(), launchResult.launchClassLoader(), "Mod class should be loaded on the transforming class loader");

        var serviceImplementations = withGameClassloader(() -> {
            return (List<Class<?>>) serviceConsumerClass.getMethod("test").invoke(null);
        });
        assertThat(serviceImplementations).containsExactly(expectedImpl);
    }
}
