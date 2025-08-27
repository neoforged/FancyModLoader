package cpw.mods.jarhandling.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cpw.mods.jarhandling.JarContents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        @MethodSource("validPaths")
        public void testContainsFile(String path, boolean exists) {
            assertEquals(exists, contents.containsFile(path));
        }

        @ParameterizedTest
        @MethodSource("invalidPaths")
        public void testContainsFileForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.containsFile(path), expectedMessage);
        }

        @ParameterizedTest
        @MethodSource("validPaths")
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
        @MethodSource("invalidPaths")
        public void testOpenFileForInvalidPath(String path, String expectedMessage) {
            assertThrowsInvalidPath(() -> contents.openFile(path), expectedMessage);
        }

        private void assertThrowsInvalidPath(Executable executable, String expectedMessage) {
            var e = assertThrows(IllegalArgumentException.class, executable);
            assertEquals(expectedMessage, e.getMessage());
        }

        static Arguments[] validPaths() {
            return new Arguments[] {
                    Arguments.of("/folder/file.txt", true),
                    Arguments.of("folder/file.txt", true),
                    Arguments.of("folder\\file.txt", true),
                    Arguments.of("folder//file.txt", true),
                    Arguments.of("folder/subfolder/file.txt", true),
                    Arguments.of("root_file.txt", true),
                    Arguments.of("/", false),
                    Arguments.of("", false),
                    Arguments.of("does_not_exist.txt", false),
            };
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
