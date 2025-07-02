/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.testlib.IdentifiableContent;
import net.neoforged.fml.testlib.RuntimeCompiler;
import net.neoforged.fml.testlib.SimulatedInstallation;
import org.junit.jupiter.api.Test;

class DistCleanerTest extends LauncherTest {
    @Test
    void testEnforceManifest() throws Exception {
        var classpath = installation.setupUserdevProject();
        var clientExtraJar = installation.getProjectRoot().resolve("client-extra.jar");

        SimulatedInstallation.writeJarFile(clientExtraJar,
                SimulatedInstallation.CLIENT_ASSETS,
                SimulatedInstallation.SHARED_ASSETS);

        assertThatThrownBy(() -> launchAndLoadWithAdditionalClasspath("neoforgeserverdev", classpath))
                .isExactlyInstanceOf(ModLoadingException.class)
                .hasMessage("""
                        Loading errors encountered:
                        \t- NeoForge dev environment client-extra jar does not have a Minecraft-Dists attribute in its manifest; this may be because you have an out-of-date gradle plugin
                        """);
    }

    @Test
    void testUserDevDistCleaning() throws Exception {
        var classpath = installation.setupUserdevProject();
        var clientExtraJar = installation.getProjectRoot().resolve("client-extra.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        manifest.getMainAttributes().putValue("Minecraft-Dists", "client server");
        var maskedResourcePath = "test/test.json";
        var maskedClassPath = "test/Masked.class";
        var loadsMaskedClassPath = "test/LoadsMasked.class";
        manifest.getEntries().put(maskedResourcePath, new Attributes());
        manifest.getEntries().put(maskedClassPath, new Attributes());
        manifest.getAttributes(maskedResourcePath).putValue("Minecraft-Dist", "client");
        manifest.getAttributes(maskedClassPath).putValue("Minecraft-Dist", "client");
        var bout = new ByteArrayOutputStream();
        manifest.write(bout);
        var manifestBytes = bout.toByteArray();

        var memoryFs = Jimfs.newFileSystem(Configuration.unix());
        try (var compiler = RuntimeCompiler.createFolder(memoryFs.getPath("/"))) {
            var compilationBuilder = compiler.builder();
            compilationBuilder = compilationBuilder.addClass("test.Masked", """
                    public class Masked {}""");
            compilationBuilder = compilationBuilder.addClass("test.LoadsMasked", """
                    public class LoadsMasked {
                        static {
                            var masked = test.Masked.class;
                        }
                    }""");
            compilationBuilder.compile();
        }

        var manifestContent = new IdentifiableContent("MANIFEST", "META-INF/MANIFEST.MF", manifestBytes);
        var maskedResourceContent = new IdentifiableContent("MASKED_RESOURCE", maskedResourcePath, "{}".getBytes(StandardCharsets.UTF_8));
        var maskedClassContent = new IdentifiableContent("MASKED_CLASS", maskedClassPath,
                Files.readAllBytes(memoryFs.getPath("/", maskedClassPath)));
        var loadsMaskedClassContent = new IdentifiableContent("LOADS_MASKED_CLASS", loadsMaskedClassPath,
                Files.readAllBytes(memoryFs.getPath("/", loadsMaskedClassPath)));

        SimulatedInstallation.writeJarFile(clientExtraJar,
                manifestContent,
                maskedResourceContent,
                maskedClassContent,
                loadsMaskedClassContent,
                SimulatedInstallation.CLIENT_ASSETS,
                SimulatedInstallation.SHARED_ASSETS);

        var result = launchAndLoadWithAdditionalClasspath("neoforgeserverdev", classpath);
        assertThat(result.issues()).isEmpty();
        var content = new ArrayList<>(List.of(
                manifestContent,
                // Masked classes are still present, as these are removed on class load for the more specific error message
                maskedClassContent,
                loadsMaskedClassContent,
                SimulatedInstallation.CLIENT_ASSETS,
                SimulatedInstallation.SHARED_ASSETS));
        content.addAll(List.of(SimulatedInstallation.USERDEV_CLIENT_JAR_CONTENT));
        assertModContent(result, "minecraft", content);
        assertThatThrownBy(() -> Class.forName("test.Masked", true, gameClassLoader))
                .isExactlyInstanceOf(ClassNotFoundException.class).hasMessage("Attempted to load class test.Masked which is not present on the dedicated server");
        assertThatThrownBy(() -> Class.forName("test.LoadsMasked", true, gameClassLoader))
                .isExactlyInstanceOf(NoClassDefFoundError.class).hasMessage("test/Masked")
                .cause().isExactlyInstanceOf(ClassNotFoundException.class).hasMessage("Attempted to load class test.Masked which is not present on the dedicated server");
    }
}
