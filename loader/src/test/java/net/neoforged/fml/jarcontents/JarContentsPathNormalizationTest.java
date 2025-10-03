/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.jarcontents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.annotation.Testable;

/**
 * Tests that different JarContent implementations correctly normalize relative paths and reject invalid paths,
 * and do so consistently.
 */
@Testable
public class JarContentsPathNormalizationTest {

    // Base class that contains the tests and is specialized for each JarContents subtype
    abstract static class NormalizationTests extends AbstractJarContentsTest {
        JarContents contents;

        abstract JarContents makeJarContents(String... files) throws IOException;

        @BeforeEach
        public void setup() throws Exception {
            contents = makeJarContents("folder/file.txt", "folder/subfolder/file.txt", "root_file.txt");
        }

        @AfterEach
        public void tearDown() throws Exception {
            contents.close();
        }

        @ParameterizedTest
        @MethodSource("filePaths")
        public void testContainsFile(String path, boolean exists) {
            assertEquals(exists, contents.containsFile(path));
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testContainsFileForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.containsFile(path), expectedMessage);
        }

        @ParameterizedTest
        @MethodSource("filePaths")
        public void testOpenFile(String path, boolean exists) throws IOException {
            try (var is = contents.openFile(path)) {
                if (exists) {
                    assertNotNull(is);
                } else {
                    assertNull(is);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("directoryPaths")
        public void testOpenFileForDirectory(String path) {
            assertThrows(IOException.class, () -> {
                try (var is = contents.openFile(path)) {
                    is.read(); // On Linux, opening a directory as a file succeeds, but the IOException is thrown on reading
                }
            });
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testOpenFileForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.openFile(path), expectedMessage);
        }

        @ParameterizedTest
        @MethodSource("filePaths")
        public void testReadFile(String path, boolean exists) throws IOException {
            var content = contents.readFile(path);
            if (exists) {
                assertNotNull(content);
            } else {
                assertNull(content);
            }
        }

        @ParameterizedTest
        @MethodSource("directoryPaths")
        public void testReadFileForDirectory(String path) {
            assertThrows(IOException.class, () -> contents.readFile(path));
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testReadFileForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.readFile(path), expectedMessage);
        }

        @ParameterizedTest
        @MethodSource("filePaths")
        public void testFindFile(String path, boolean exists) {
            var uri = contents.findFile(path);
            if (exists) {
                assertThat(uri).isPresent();
            } else {
                assertThat(uri).isEmpty();
            }
        }

        @ParameterizedTest
        @MethodSource("directoryPaths")
        public void testFindFileForDirectory(String path) {
            assertThat(contents.findFile(path)).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testFindFileForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.findFile(path), expectedMessage);
        }

        @ParameterizedTest
        @MethodSource("filePaths")
        public void testGet(String path, boolean exists) {
            var resource = contents.get(path);
            if (exists) {
                assertNotNull(resource);
            } else {
                assertNull(resource);
            }
        }

        @ParameterizedTest
        @MethodSource("directoryPaths")
        public void testGetForDirectory(String path) {
            assertNull(contents.get(path));
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testGetForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.get(path), expectedMessage);
        }

        @ParameterizedTest
        @MethodSource("filePaths")
        public void testVisitContentWithFileAsStartingPoint(String path) {
            // Trying to visit from a file should do nothing, whether it exists or not
            contents.visitContent(path, (relativePath, resource) -> {
                fail("Should not visit any files when starting from a file");
            });
        }

        @ParameterizedTest
        @MethodSource("directoryPaths")
        public void testVisitContentWithFolderAsStartingPoint(String path) {
            var visited = new ArrayList<String>();
            contents.visitContent(path, (relativePath, resource) -> visited.add(relativePath));
            assertThat(visited).isNotEmpty();
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testVisitContentForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.visitContent(path, (relativePath, resource) -> {}), expectedMessage);
        }

        private void assertThrowsInvalidPath(Executable executable, String expectedMessage) {
            var e = assertThrows(IllegalArgumentException.class, executable);
            assertEquals(expectedMessage, e.getMessage());
        }

        static Arguments[] filePaths() {
            // Second element is whether the file is expected to exist
            return new Arguments[] {
                    Arguments.of("/folder/file.txt", true),
                    Arguments.of("folder/file.txt", true),
                    Arguments.of("folder\\file.txt", true),
                    Arguments.of("folder//file.txt", true),
                    Arguments.of("folder/subfolder/file.txt", true),
                    Arguments.of("root_file.txt", true),
                    Arguments.of("does_not_exist.txt", false),
            };
        }

        /**
         * Empty string (and non-normalized forms of it) refer to the root directory and should be treated as such.
         */
        static String[] directoryPaths() {
            return new String[] { "/", "///", "", "folder", "/folder", "/folder/", "folder/" };
        }

        static Arguments[] invalidPaths() {
            // First element is path, second element is expected exception message
            return new Arguments[] {
                    Arguments.of("folder/../file.txt", "./ or ../ segments in paths are not supported"),
                    Arguments.of("./file.txt", "./ or ../ segments in paths are not supported")
            };
        }
    }

    @Nested
    class FolderJarContentsTest extends NormalizationTests {
        @Override
        JarContents makeJarContents(String... files) throws IOException {
            for (String file : files) {
                Path filePath = tempDir.resolve(file);
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }
            return JarContents.ofPath(tempDir);
        }
    }

    @Nested
    class JarFileContentsTest extends NormalizationTests {
        @Override
        JarContents makeJarContents(String... files) throws IOException {
            for (String file : files) {
                Path filePath = tempDir.resolve(file);
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }
            return JarContents.ofPath(makeJar());
        }
    }
}
