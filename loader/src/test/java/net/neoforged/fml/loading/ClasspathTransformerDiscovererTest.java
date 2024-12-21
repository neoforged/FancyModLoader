/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;

import cpw.mods.modlauncher.api.NamedPath;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClasspathTransformerDiscovererTest {
    // A service locator file that should be picked up by the locator
    private static final IdentifiableContent ML_SERVICE_FILE = new IdentifiableContent("ML_SERVICE", "META-INF/services/cpw.mods.modlauncher.api.ITransformationService", "some.Class".getBytes());

    SimulatedInstallation simulatedInstallation;
    private Path mlServicesJar;
    private List<Path> gradleModule;

    @BeforeEach
    void setUp() throws IOException {
        simulatedInstallation = new SimulatedInstallation();

        // Add a ML service candidate jar
        mlServicesJar = simulatedInstallation.getProjectRoot().resolve("ml-services.jar");

        // Add an exploded ML service ONLY in the modFolders property, but not on the classpath
        gradleModule = simulatedInstallation.setupGradleModule(ML_SERVICE_FILE);
    }

    @Test
    void testLocateTransformerServiceInDev() throws Exception {
        var candidates = runLocator("neoforgeclientdev");

        assertThat(candidates)
                .anySatisfy(candidate -> {
                    assertThat(candidate.name()).isEqualTo(mlServicesJar.toUri().toString());
                    assertThat(candidate.paths()).containsOnly(mlServicesJar);
                });
        assertThat(candidates)
                .anySatisfy(candidate -> {
                    assertThat(candidate.paths()).containsOnly(gradleModule.toArray(Path[]::new));
                });
    }

    @Test
    void testLocateTransformerServiceNotInDev() throws Exception {
        var candidates = runLocator("neoforgeclient");
        assertThat(candidates).isEmpty();
    }

    private List<NamedPath> runLocator(String launchTarget) throws IOException {
        var locator = new ClasspathTransformerDiscoverer();

        SimulatedInstallation.writeJarFile(mlServicesJar, ML_SERVICE_FILE);
        SimulatedInstallation.setModFoldersProperty(Map.of("ML_SERVICES_FROM_DIR", gradleModule));

        List<NamedPath> candidates;
        var cl = new URLClassLoader(new URL[] {
                mlServicesJar.toUri().toURL()
        });
        var previousCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(cl);
        try {
            return locator.candidates(simulatedInstallation.getGameDir(), launchTarget);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }
}
