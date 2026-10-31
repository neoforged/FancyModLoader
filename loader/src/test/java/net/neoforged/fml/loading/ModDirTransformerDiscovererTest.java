/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;

import cpw.mods.modlauncher.api.NamedPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.testutils.RequiresSymlink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModDirTransformerDiscovererTest {
    private static final IdentifiableContent ML_SERVICE_FILE = new IdentifiableContent("ML_SERVICE", "META-INF/services/cpw.mods.modlauncher.api.ITransformationService", "some.Class".getBytes());

    SimulatedInstallation simulatedInstallation;

    @BeforeEach
    void setUp() throws IOException {
        simulatedInstallation = new SimulatedInstallation();
    }

    @AfterEach
    void tearDown() throws Exception {
        simulatedInstallation.close();
    }

    @Test
    void testEarlyServiceInModsFolder() throws Exception {
        var serviceJar = simulatedInstallation.getModsFolder().resolve("service.jar");
        SimulatedInstallation.writeJarFile(serviceJar, ML_SERVICE_FILE);

        assertFound(runDiscoverer(), serviceJar);
    }

    @Test
    @RequiresSymlink
    void testEarlyServiceInSymlinkedModsFolder(@TempDir Path symlinkTarget) throws Exception {
        var modsFolder = simulatedInstallation.getModsFolder();
        Files.delete(modsFolder);
        Files.createSymbolicLink(modsFolder, symlinkTarget);

        var serviceJar = symlinkTarget.resolve("service.jar");
        SimulatedInstallation.writeJarFile(serviceJar, ML_SERVICE_FILE);

        assertFound(runDiscoverer(), serviceJar);
    }

    private List<NamedPath> runDiscoverer() {
        return new ModDirTransformerDiscoverer().candidates(simulatedInstallation.getGameDir(), "forgeclient");
    }

    private static void assertFound(List<NamedPath> candidates, Path expectedJar) throws IOException {
        var expectedRealPath = expectedJar.toRealPath();
        assertThat(candidates)
                .anySatisfy(candidate -> {
                    assertThat(candidate.name()).isEqualTo("service.jar");
                    assertThat(candidate.paths()).hasSize(1);
                    assertThat(candidate.paths()[0].toRealPath()).isEqualTo(expectedRealPath);
                });
    }
}
