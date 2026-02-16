/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import net.neoforged.fml.testlib.ModFileBuilder;
import net.neoforged.fml.testlib.SimulatedInstallation;
import net.neoforged.fml.testlib.args.ClientInstallationTypesSource;
import net.neoforged.fml.testlib.args.InstallationTypeSource;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.junit.jupiter.params.ParameterizedTest;

public class EarlyServicesTest extends LauncherTest {
    public static ThreadLocal<Boolean> WAS_CALLED = new ThreadLocal<>();

    private static final ContainedJarIdentifier JIJ_TESTMOD_ID = new ContainedJarIdentifier("testmod", "testmod");

    // This test checks that a GraphicsBootstrapper service will be picked up in our various scenarios (jar / classpath)
    @ParameterizedTest
    @ClientInstallationTypesSource
    void testGraphicsBootstrapper(SimulatedInstallation.Type type) throws Exception {
        installation.setup(type);

        installation.buildInstallationAppropriateNonModProject(null, "bootstrap.jar", EarlyServicesTest::addEarlyService);

        headless = false;
        launchInstalledDist();

        assertEquals(Boolean.TRUE, WAS_CALLED.get());
    }

    // Tests that an early service jar that also declares a mod will not be picked up twice.
    @ParameterizedTest
    @ClientInstallationTypesSource
    void testGraphicsBootstrapperWithMod(SimulatedInstallation.Type type) throws Exception {
        installation.setup(type);

        installation.buildInstallationAppropriateModProject("bootwithmod", "bootwithmod.jar", builder -> {
            addEarlyService(builder);
            builder.withTestmodModsToml();
        });

        headless = false;
        var launchResult = launchInstalledDist();

        assertEquals(Boolean.TRUE, WAS_CALLED.get());
        assertThat(launchResult.loadedMods()).containsOnlyKeys("minecraft", "neoforge");
    }

    // This test checks that a GraphicsBootstrapper service will be picked up in our various scenarios (jar / classpath),
    // and its packaged JIJ mod will also be loaded.
    @ParameterizedTest
    @InstallationTypeSource({
            // JIJ is not loaded from folders on classpath, since they lack the grouping
            // But production and general jars on classpath should work.
            SimulatedInstallation.Type.PRODUCTION_CLIENT,
            SimulatedInstallation.Type.PRODUCTION_SERVER,
            SimulatedInstallation.Type.USERDEV_JAR
    })
    void testGraphicsBootstrapperWithJarInJarMod(SimulatedInstallation.Type type) throws Exception {
        installation.setup(type);

        installation.buildInstallationAppropriateNonModProject(null, "bootstrap.jar", builder -> {
            addEarlyService(builder);
            builder.withJarInJar(JIJ_TESTMOD_ID, jijBuilder -> {
                jijBuilder.withTestmodModsToml();
            });
        });

        headless = false;
        var result = launchInstalledDist();

        assertEquals(Boolean.TRUE, WAS_CALLED.get());
        assertThat(result.loadedMods()).containsKey("testmod");
    }

    private static void addEarlyService(ModFileBuilder<?> builder) throws IOException {
        builder.addClass("bootstrap.Bootstrapper", """
                public class Bootstrapper implements net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper {
                    @Override
                    public String name() {
                        return "test";
                    }

                    @Override
                    public void bootstrap(String[] arguments) {
                        net.neoforged.fml.loading.EarlyServicesTest.WAS_CALLED.set(true);
                    }
                }
                """)
                .addService(GraphicsBootstrapper.class, "bootstrap.Bootstrapper");
    }
}
