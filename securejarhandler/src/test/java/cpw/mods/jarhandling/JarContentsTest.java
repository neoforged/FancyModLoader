package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.CompositeJarContents;
import cpw.mods.jarhandling.impl.EmptyJarContents;
import cpw.mods.jarhandling.impl.FolderJarContents;
import cpw.mods.jarhandling.impl.JarFileContents;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JarContentsTest {
    @TempDir
    Path tempDir;

    @Nested
    class OfFilteredPaths {
        @Test
        void testEmptyCollection() {
            // An empty collection is invalid because each JarContents has a primary path as its identity.
            assertThatThrownBy(() -> JarContents.ofFilteredPaths(List.of()))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot construct jar contents without any paths.");
        }

        @Test
        void testSingleExistingPathWithoutFilter() throws IOException {
            // A single path with no filter should be optimized to just return the JarContents for that path.
            JarContents contents = JarContents.ofFilteredPaths(List.of(
                    new JarContents.FilteredPath(tempDir),
                    new JarContents.FilteredPath(tempDir.resolve("Doesnotexist"), relativePath -> true)));
            assertThat(contents).isExactlyInstanceOf(FolderJarContents.class);
        }

        @Test
        void testNonExistingPathsAreSkipped() throws IOException {
            var jar1 = createEmptyJar("jar1.jar");
            var jar2 = createEmptyJar("jar2.jar");

            // It is possible to construct a combined jar, where some paths do not exist.
            try (var contents = JarContents.ofFilteredPaths(List.of(
                    new JarContents.FilteredPath(tempDir.resolve("nonexistent1.jar")),
                    new JarContents.FilteredPath(jar1),
                    new JarContents.FilteredPath(tempDir.resolve("nonexistent2.jar")),
                    new JarContents.FilteredPath(jar2)
            ))) {
                assertThat(contents.getPrimaryPath()).isEqualTo(jar1);
                assertThat(contents.getContentRoots()).containsExactly(jar1, jar2);
            }
        }

        @Test
        void testOnePathMustExist() {
            // At least one path must exist, otherwise there is no way to determine the primary path.
            assertThatThrownBy(() -> JarContents.ofFilteredPaths(List.of(
                    new JarContents.FilteredPath(tempDir.resolve("nonexistent1.jar")),
                    new JarContents.FilteredPath(tempDir.resolve("nonexistent2.jar"))
            ))).isExactlyInstanceOf(NoSuchFileException.class)
                    .hasMessageContaining("At least one of the paths must exist when constructing jar contents");
        }
    }

    @Nested
    class OfPaths {
        @Test
        void testEmptyCollection() {
            // An empty collection is invalid because each JarContents has a primary path as its identity.
            assertThatThrownBy(() -> JarContents.ofPaths(List.of()))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot construct jar contents without any paths.");
        }

        @Test
        void testSingleExistingPathWithoutFilter() throws IOException {
            // A single path with no filter should be optimized to just return the JarContents for that path.
            JarContents contents = JarContents.ofPaths(List.of(tempDir, tempDir.resolve("Doesnotexist")));
            assertThat(contents).isExactlyInstanceOf(FolderJarContents.class);
        }

        @Test
        void testNonExistingPathsAreSkipped() throws IOException {
            var jar1 = createEmptyJar("jar1.jar");
            var jar2 = createEmptyJar("jar2.jar");

            // It is possible to construct a combined jar, where some paths do not exist.
            try (var contents = JarContents.ofPaths(List.of(tempDir.resolve("nonexistent1.jar"), jar1, tempDir.resolve("nonexistent2.jar"), jar2))) {
                assertThat(contents).isExactlyInstanceOf(CompositeJarContents.class);
                assertThat(contents.getPrimaryPath()).isEqualTo(jar1);
                assertThat(contents.getContentRoots()).containsExactly(jar1, jar2);
            }
        }

        @Test
        void testOnePathMustExist() {
            // At least one path must exist, otherwise there is no way to determine the primary path.
            assertThatThrownBy(() -> JarContents.ofPaths(List.of(tempDir.resolve("nonexistent1.jar"), tempDir.resolve("nonexistent2.jar"))))
                    .isExactlyInstanceOf(NoSuchFileException.class)
                    .hasMessageContaining("At least one of the paths must exist when constructing jar contents");
        }
    }

    @Nested
    class OfPath {
        @Test
        void testFileMustExist() {
            assertThatThrownBy(() -> JarContents.ofPath(tempDir.resolve("nonexistent1.jar")))
                    .isExactlyInstanceOf(NoSuchFileException.class)
                    .hasMessageContaining("Cannot construct mod container from missing");
        }

        @Test
        void testJarFile() throws IOException {
            // Note that the file just must exist, it doesn't need to have a specific extension
            var path = createEmptyJar("test");
            try (var contents = JarContents.ofPath(path)) {
                assertThat(contents).isExactlyInstanceOf(JarFileContents.class);
                assertThat(contents.getPrimaryPath()).isEqualTo(path);
                assertThat(contents.getContentRoots()).containsExactly(path);
            }
        }

        @Test
        void testFolder() throws IOException {
            // Note that the file just must exist, it doesn't need to have a specific extension
            try (var contents = JarContents.ofPath(tempDir)) {
                assertThat(contents).isExactlyInstanceOf(FolderJarContents.class);
                assertThat(contents.getPrimaryPath()).isEqualTo(tempDir);
                assertThat(contents.getContentRoots()).containsExactly(tempDir);
            }
        }
    }

    @Nested
    class Empty {
        @Test
        void test() throws IOException {
            Path path = tempDir.resolve("nonexistent.jar");
            try (var contents = JarContents.empty(path)) {
                assertThat(contents).isExactlyInstanceOf(EmptyJarContents.class);
                assertThat(contents.getPrimaryPath()).isEqualTo(path);
            }
        }

        @Test
        void testCantBeNull() {
            assertThrows(NullPointerException.class, () -> JarContents.empty(null));
        }
    }

    private Path createEmptyJar(String name) throws IOException {
        var path = tempDir.resolve(name);
        new JarOutputStream(Files.newOutputStream(path)).close();
        return path;
    }
}
