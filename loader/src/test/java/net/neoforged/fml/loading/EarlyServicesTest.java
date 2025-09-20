/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.fml.testlib.SimulatedInstallation;
import net.neoforged.fml.testlib.args.ClientInstallationTypesSource;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.junit.jupiter.params.ParameterizedTest;

public class EarlyServicesTest extends LauncherTest {
    public static ThreadLocal<Boolean> WAS_CALLED = new ThreadLocal<>();

    // This test checks that a GraphicsBootstrapper service will be picked up in our various scenarios (jar / classpath)
    @ParameterizedTest
    @ClientInstallationTypesSource
    void testGraphicsBootstrapper(SimulatedInstallation.Type type) throws Exception {
        installation.setup(type);

        installation.buildInstallationAppropriateNonModProject(null, "bootstrap.jar", builder -> builder.addClass("bootstrap.Bootstrapper", """
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
                .addService(GraphicsBootstrapper.class, "bootstrap.Bootstrapper"));

        headless = false;
        launchInstalledDist();

        assertEquals(Boolean.TRUE, WAS_CALLED.get());
    }
}
