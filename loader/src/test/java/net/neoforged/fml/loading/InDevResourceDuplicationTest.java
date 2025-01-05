/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import net.neoforged.neoforgespi.locating.IModFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks that the resources contained by plugins or mods on the classpath are not found twice by
 * the game loader. This could occur if the plugin/mod gets "re"-loaded onto the TransformingClassloader.
 */
class InDevResourceDuplicationTest extends LauncherTest {
    Path modJar;
    Path pluginJar;

    @BeforeEach
    void setupJars() throws IOException {
        pluginJar = installation.buildModJar("../plugin.jar")
                .withModTypeManifest(IModFile.Type.LIBRARY)
                .addTextFile("helloworld.txt", "PLUGIN_RESOURCE")
                .build();

        modJar = installation.buildModJar("../mod.jar")
                .withTestmodModsToml()
                .addTextFile("helloworld.txt", "MOD_RESOURCE")
                .build();
    }

    @Test
    void inDevelopment() throws Exception {
        var classpath = installation.setupUserdevProject();
        Collections.addAll(classpath, modJar, pluginJar);

        var launchResult = launchAndLoadWithAdditionalClasspath(LaunchMode.DEV_CLIENT, classpath);

        var resources = ClassLoaderTestUtils.getResources(launchResult.launchClassLoader(), "helloworld.txt");
        assertThat(resources).containsExactlyInAnyOrder("MOD_RESOURCE", "PLUGIN_RESOURCE");
    }
}
