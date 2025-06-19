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
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import net.neoforged.fml.test.RuntimeCompiler;
import org.junit.jupiter.api.Test;

class DistCleanerTest extends LauncherTest {
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
        var compiler = new RuntimeCompiler(memoryFs);
        var compilationBuilder = compiler.builder();
        compilationBuilder.addClass("test.Masked", """
                public class Masked {}""");
        compilationBuilder.addClass("test.LoadsMasked", """
                public class LoadsMasked {
                    static {
                        var masked = test.Masked.class;
                    }
                }""");
        compilationBuilder.compile();

        var manifestContent = new IdentifiableContent("MANIFEST", "META-INF/MANIFEST.MF", manifestBytes);
        var maskedResourceContent = new IdentifiableContent("MASKED_RESOURCE", maskedResourcePath, "{}".getBytes(StandardCharsets.UTF_8));
        var maskedClassContent = new IdentifiableContent("MASKED_CLASS", maskedClassPath,
                Files.readAllBytes(memoryFs.getPath("/", maskedClassPath)));
        var loadsMaskedClassContent = new IdentifiableContent("LOADS_MASKED_CLASS", loadsMaskedClassPath,
                Files.readAllBytes(memoryFs.getPath("/", loadsMaskedClassPath)));
        var clientAssetsContent = new IdentifiableContent("CLIENT_ASSETS", "assets/.mcassetsroot");
        var sharedAssetsContent = new IdentifiableContent("SHARED_ASSETS", "data/.mcassetsroot");

        SimulatedInstallation.writeJarFile(clientExtraJar,
                manifestContent,
                maskedResourceContent,
                maskedClassContent,
                loadsMaskedClassContent,
                clientAssetsContent,
                sharedAssetsContent);

        var result = launchAndLoadWithAdditionalClasspath("neoforgeserverdev", classpath);
        assertThat(result.issues()).isEmpty();
        installation.assertModContent(result, "minecraft", List.of(
                manifestContent,
                // Masked classes are still present, as these are removed on class load for the more specific error message
                maskedClassContent,
                loadsMaskedClassContent,
                clientAssetsContent,
                sharedAssetsContent,
                // Other resources from the main jar
                SimulatedInstallation.generateClass("PATCHED_CLIENT", "net/minecraft/client/Minecraft.class"),
                SimulatedInstallation.generateClass("PATCHED_SHARED", "net/minecraft/server/MinecraftServer.class")));
        assertThatThrownBy(() -> Class.forName("test.Masked", true, gameClassLoader))
                .isExactlyInstanceOf(ClassNotFoundException.class).hasMessage("Attempted to load class test.Masked which is not present on the dedicated server");
        assertThatThrownBy(() -> Class.forName("test.LoadsMasked", true, gameClassLoader))
                .isExactlyInstanceOf(NoClassDefFoundError.class).hasMessage("test/Masked")
                .cause().isExactlyInstanceOf(ClassNotFoundException.class).hasMessage("Attempted to load class test.Masked which is not present on the dedicated server");
    }
}
