/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.classloading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.neoforged.fml.testlib.SimulatedInstallation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResourceMaskingClassLoaderTest {
    SimulatedInstallation installation;
    URLClassLoader simulatedAppClassLoader;
    Path maskedJar;
    Path unmaskedJar;
    Path maskedDir;
    ResourceMaskingClassLoader maskedLoader;

    @BeforeEach
    void setUp() throws Exception {
        installation = new SimulatedInstallation();

        maskedJar = installation.buildModJar("masked.jar")
                .addTextFile("folder/resource.txt", "MASKED_JAR")
                .addTextFile("folder/exclusive_masked_resource.txt", "MASKED_JAR")
                .addTextFile("rootresource.txt", "MASKED_JAR")
                .build();
        unmaskedJar = installation.buildModJar("unmasked.jar")
                .addTextFile("folder/resource.txt", "UNMASKED_JAR")
                .addTextFile("rootresource.txt", "UNMASKED_JAR")
                .addTextFile("unmasked_file.txt", "UNMASKED_JAR")
                .build();
        maskedDir = installation.getGameDir().resolve("masked_dir");
        Files.createDirectories(maskedDir);
        Files.createDirectories(maskedDir.resolve("folder"));
        Files.writeString(maskedDir.resolve("folder/resource.txt"), "MASKED_FOLDER");
        Files.writeString(maskedDir.resolve("folder/exclusive_masked_resource.txt"), "MASKED_FOLDER");
        simulatedAppClassLoader = new URLClassLoader(new URL[] {
                maskedJar.toUri().toURL(),
                maskedDir.toUri().toURL(),
                unmaskedJar.toUri().toURL()
        }, ClassLoader.getPlatformClassLoader());

        maskedLoader = new ResourceMaskingClassLoader(simulatedAppClassLoader, Set.of(maskedJar, maskedDir));

        // Avoids being unable to delete the temp directory because of cached JarFiles held by JarURLConnection
        URLConnection.setDefaultUseCaches("jar", false);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (simulatedAppClassLoader != null) {
            simulatedAppClassLoader.close();
        }
        if (installation != null) {
            installation.close();
        }
    }

    private static final String TESTS = """
            # This resource exists in all three classpath locations, and should be returned from the unmasked jar
            folder/resource.txt,UNMASKED_JAR
            # This resource exists only in the unmasked classpath location and should be returned normally
            unmasked_file.txt,UNMASKED_JAR
            # This resource exists only in both of the masked locations and should be filtered out
            folder/exclusive_masked_resource.txt,
            # This resource does not exist at all
            missing_file.txt,
            """;

    @ParameterizedTest
    @CsvSource(textBlock = TESTS)
    void testGetResource(String requestedPath, String expectedContent) throws Exception {
        assertEquals(expectedContent, ClassLoaderTestUtils.getResource(maskedLoader, requestedPath));
    }

    @ParameterizedTest
    @CsvSource(textBlock = TESTS)
    void testGetResources(String requestedPath, String expectedResult) throws Exception {
        var resources = ClassLoaderTestUtils.getResources(maskedLoader, requestedPath);
        if (expectedResult != null) {
            assertThat(resources).containsExactly(expectedResult);
        } else {
            assertThat(resources).isEmpty();
        }
    }
}
