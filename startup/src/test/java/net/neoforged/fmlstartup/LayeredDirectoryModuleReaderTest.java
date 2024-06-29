/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LayeredDirectoryModuleReaderTest {
    @TempDir
    Path tempDir;

    Path a;
    Path b;
    LayeredDirectoryModuleReader reader;

    @BeforeEach
    void setUp() throws IOException {
        a = tempDir.resolve("a");
        b = tempDir.resolve("b");

        createFiles(a, "net/neoforged/fml/FMLLoader.class", "a/B.class", ".b/AB.class", "a./AB.class");
        createFiles(b, "log4j2.xml");
        createFiles(b, "META-INF/MANIFEST.MF");

        reader = new LayeredDirectoryModuleReader(List.of(a.toFile(), b.toFile()));
    }

    /**
     * See the docs on ModuleFinder for the reasoning behind these tests.
     * In particular, it attempts at preventing escape from the directory via . and .. segments.
     * We also ensure the path does not contain backslashes as that would work differently on Windows.
     */
    @ParameterizedTest
    @CsvSource(textBlock = """
            '',,
            '..',,
            'net/','a','net/'
            './',,
            'net/./neoforged/',,
            'net/../net/',,
            'net//',,
            '/',,
            'net','a','net/'
            'a/B.class','a','a/B.class'
            '.b/AB.class','a','.b/AB.class'
            'a./AB.class','a','a./AB.class'
            'net/neoforged/fml/FMLLoader.class','a','net/neoforged/fml/FMLLoader.class'
            'net\\neoforged/fml/FMLLoader.class',,
            'net/neoforged/fml/FMLLoader.class/',,
            'log4j2.xml','b','log4j2.xml'
            'META-INF/','b','META-INF/'
            'META-INF/MANIFEST.MF','b','META-INF/MANIFEST.MF'
            """)
    void testFind(String name, String expectedRootName, String expectedRelativePath) throws Exception {
        var result = reader.find(name);
        if (expectedRootName == null) {
            assertEquals(Optional.empty(), result);
            return;
        }

        var expectedRoot = switch (expectedRootName) {
            case "a" -> a;
            case "b" -> b;
            default -> throw new IllegalArgumentException("Invalid expected root: " + expectedRootName);
        };
        var expectedUri = URI.create(expectedRoot.toUri() + expectedRelativePath);
        assertEquals(Optional.of(expectedUri), reader.find(name));
    }

    private void createFiles(Path baseDir, String... relativePaths) throws IOException {
        for (String relativePath : relativePaths) {
            var p = baseDir.resolve(relativePath);
            Files.createDirectories(p.getParent());
            Files.writeString(p, "");
        }
    }
}
