/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.NamedPath;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClasspathTransformerDiscovererTest {
    // A service locator file that should be picked up by the locator
    private static final IdentifiableContent ML_SERVICE_FILE = new IdentifiableContent("ML_SERVICE", "META-INF/services/cpw.mods.modlauncher.api.ITransformationService", "some.Class".getBytes());

    SimulatedInstallation simulatedInstallation;
    private Path mlServicesJar;
    private List<Path> gradleModule;

    @BeforeAll
    static void openJavaInvoke() {
        ByteBuddyAgent.install().redefineModule(
                MethodHandle.class.getModule(),
                Set.of(),
                Map.of(),
                Map.of(
                        MethodHandle.class.getPackageName(),
                        Set.of(SecureJar.class.getModule())),
                Set.of(),
                Map.of());
    }

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
        var candidates = runLocator(LauncherTest.LaunchMode.DEV_CLIENT);

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
        var candidates = runLocator(LauncherTest.LaunchMode.PROD_CLIENT);
        assertThat(candidates).isEmpty();
        Assertions.fail();
    }

    private List<NamedPath> runLocator(LauncherTest.LaunchMode launchTarget) throws IOException {
        return List.of();

//        var locator = new ClasspathTransformerDiscoverer();

//        SimulatedInstallation.writeJarFile(mlServicesJar, ML_SERVICE_FILE);
//        SimulatedInstallation.setModFoldersProperty(Map.of("ML_SERVICES_FROM_DIR", gradleModule));

//        List<NamedPath> candidates;
//        var cl = new URLClassLoader(new URL[] {
//                mlServicesJar.toUri().toURL()
//        });
//        var previousCl = Thread.currentThread().getContextClassLoader();
//        Thread.currentThread().setContextClassLoader(cl);
//        try {
//            return locator.candidates(simulatedInstallation.getGameDir(), launchTarget);
//        } finally {
//            Thread.currentThread().setContextClassLoader(previousCl);
//        }
    }
}
