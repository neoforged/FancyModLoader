/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;

import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EarlyServiceDiscoveryTest {
    // A service locator file that should be picked up by the locator
    private static final IdentifiableContent FML_SERVICE_FILE = new IdentifiableContent(
            "FML_SERVICE",
            "META-INF/services/net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
            "some.Class".getBytes());

    SimulatedInstallation simulatedInstallation;
    private Path earlyServiceJar;
    private Path nonEarlyServiceJar;

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

        // Add a FML service candidate jar
        earlyServiceJar = simulatedInstallation.getModsFolder().resolve("services.jar");
        // Add a jar that does NOT contain services
        nonEarlyServiceJar = simulatedInstallation.getModsFolder().resolve("non-services.jar");
    }

    @Test
    void testLocateEarlyServices() throws Exception {
        var candidates = runLocator();

        assertThat(candidates).containsOnly(earlyServiceJar);
    }

    private List<Path> runLocator() throws IOException {
        SimulatedInstallation.writeJarFile(earlyServiceJar, FML_SERVICE_FILE);
        SimulatedInstallation.writeJarFile(nonEarlyServiceJar);
        return EarlyServiceDiscovery.findEarlyServices(simulatedInstallation.getModsFolder());
    }
}
