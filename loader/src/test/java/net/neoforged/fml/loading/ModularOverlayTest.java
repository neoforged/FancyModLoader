/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ModularOverlayTest extends LauncherTest {
    @AfterEach
    public void attemptCleanup() {
        System.gc(); // It seems impossible to explicitly close a module layer
    }

    @Test
    void testModularLoaderOverlaysParent() throws Exception {
        var testJar = installation.buildModJar("test.jar")
                .withManifest(Map.of(
                        "Manifest-Version", "1.0",
                        "Automatic-Module-Name", "testmodule"))
                .addClass("testmod.TestService", """
                        public class TestService implements Runnable {
                            public void run() {
                            }
                        }
                        """)
                .addTextFile("META-INF/services/java.lang.Runnable", """
                        testmod.TestService
                        """)
                .build();

        // Simulate the parent class-loader by using an isolated URLClassLoader
        try (var root = new URLClassLoader(new URL[] { testJar.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            var configuration = Configuration.resolveAndBind(
                    ModuleFinder.of(testJar),
                    List.of(ModuleLayer.boot().configuration()),
                    ModuleFinder.of(),
                    List.of("testmodule"));

            var moduleLayer = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()), root);

            var modularLoader = moduleLayer.layer().findLoader("testmodule");

            var enums = modularLoader.getResources("META-INF/services/java.lang.Runnable");
            var urls = new ArrayList<URL>();
            while (enums.hasMoreElements()) {
                urls.add(enums.nextElement());
            }

            var services = ServiceLoader.load(Runnable.class, modularLoader).stream().toList();
            assertThat(services).hasSize(1);
            assertEquals("testmodule", services.get(0).get().getClass().getModule().getName());
        }
    }
}
