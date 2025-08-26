package cpw.mods.jarhandling.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContents.FilteredPath;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CompositeJarContentsTest extends AbstractJarContentsTest {
    private Path path1;
    private Path path2;
    private Path path3;

    @BeforeEach
    void setUp() throws IOException {
        // Set up three folders with overlapping and unique files
        path1 = tempDir.resolve("folder1");
        path2 = tempDir.resolve("folder2");
        path3 = tempDir.resolve("folder3");

        // Folder 1 contents
        writeTextFile("folder1/file1.txt", "file1 from folder1");
        writeTextFile("folder1/shared.txt", "shared from folder1");
        writeTextFile("folder1/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFrom: folder1\n");

        // Folder 2 contents
        writeTextFile("folder2/file2.txt", "file2 from folder2");
        writeTextFile("folder2/shared.txt", "shared from folder2");
        writeTextFile("folder2/subdir/nested.txt", "nested from folder2");

        // Folder 3 contents
        writeTextFile("folder3/file3.txt", "file3 from folder3");
        writeTextFile("folder3/shared.txt", "shared from folder3");
        writeTextFile("folder3/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFrom: folder3\n");
    }

    @Test
    void testEmptyDelegatesThrows() {
        assertThatThrownBy(() -> JarContents.ofPaths(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot construct jar contents without any paths.");
        assertThatThrownBy(() -> JarContents.ofFilteredPaths(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot construct jar contents without any paths.");
    }

    @Test
    void testMismatchedFiltersThrows() {
        // This test is no longer relevant since FilteredPath bundles path and filter together
        // Remove this test or replace with a different validation test
    }

    @Test
    void testGetPrimaryPath() throws IOException {
        // The primary path should be from the first delegate in the user-supplied list
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            assertEquals(path1, composite.getPrimaryPath());
        }
    }

    @Test
    void testGetContentRoots() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            assertThat(composite.getContentRoots())
                    .containsExactlyInAnyOrder(path1, path2, path3);
        }
    }

    @Test
    void testDelegationOrder() throws IOException {
        // Later entries override earlier entries
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            // shared.txt exists in all three, should get from folder3 (last one)
            var sharedContent = composite.readFile("shared.txt");
            assertNotNull(sharedContent);
            assertEquals("shared from folder3", new String(sharedContent));
        }
    }

    @Test
    void testGetUniqueFiles() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            // Each unique file should be accessible
            assertNotNull(composite.get("file1.txt"));
            assertNotNull(composite.get("file2.txt"));
            assertNotNull(composite.get("file3.txt"));
        }
    }

    @Test
    void testContainsFile() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            assertTrue(composite.containsFile("file1.txt"));
            assertTrue(composite.containsFile("file2.txt"));
            assertTrue(composite.containsFile("file3.txt"));
            assertTrue(composite.containsFile("shared.txt"));
            assertFalse(composite.containsFile("nonexistent.txt"));
        }
    }

    @Test
    void testFindFile() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            assertThat(composite.findFile("file1.txt")).isPresent();
            assertThat(composite.findFile("shared.txt")).isPresent();
            assertThat(composite.findFile("nonexistent.txt")).isEmpty();
        }
    }

    @Test
    void testGetManifest() throws IOException {
        // Should get manifest from the last delegate (folder3)
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            Manifest manifest = composite.getManifest();

            assertNotNull(manifest);
            assertEquals("folder3", manifest.getMainAttributes().getValue("From"));
        }
    }

    @Test
    void testGetManifestFallback() throws IOException {
        // If later delegates don't have manifest, should fall back to earlier ones
        try (var composite = new CompositeJarContents(List.of(JarContents.ofPath(path1), new EmptyJarContents(Path.of(""))))) {
            Manifest manifest = composite.getManifest();

            assertNotNull(manifest);
            assertEquals("folder1", manifest.getMainAttributes().getValue("From"));
        }
    }

    @Test
    void testVisitContent() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            var visited = new ArrayList<String>();

            composite.visitContent("", (relativePath, resource) -> visited.add(relativePath));

            // Should visit all unique files
            assertThat(visited)
                    .containsExactlyInAnyOrder(
                            "META-INF/MANIFEST.MF",
                            "file1.txt",
                            "file2.txt",
                            "file3.txt",
                            "shared.txt",
                            "subdir/nested.txt");
        }
    }

    @Test
    void testVisitContentFromSubfolder() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            var visited = new ArrayList<String>();

            composite.visitContent("subdir", (relativePath, resource) -> visited.add(relativePath));

            assertThat(visited).containsExactly("subdir/nested.txt");
        }
    }

    @Test
    void testClose() throws IOException {
        var delegate = JarContents.ofPath(path1);
        var spy = Mockito.spy(delegate);
        new CompositeJarContents(List.of(spy)).close();
        verify(spy).close();
    }

    @Test
    void testToString() throws IOException {
        try (var delegate1 = JarContents.ofPath(path1);
                var delegate2 = JarContents.ofPath(path2);
                var composite = new CompositeJarContents(List.of(delegate1, delegate2))) {
            var result = composite.toString();

            assertThat(result)
                    .startsWith("[")
                    .endsWith("]")
                    .contains(delegate1.toString())
                    .contains(delegate2.toString());
        }
    }

    @Nested
    class WithFilters {
        private JarContents.PathFilter filter1;
        private JarContents.PathFilter filter2;
        private JarContents.PathFilter filter3;

        @BeforeEach
        void setUp() {
            // Filter out specific files from each delegate
            filter1 = path -> !path.equals("file1.txt"); // Hide file1.txt from folder1
            filter2 = path -> !path.startsWith("subdir/"); // Hide subdir from folder2
            filter3 = path -> !path.equals("META-INF/MANIFEST.MF"); // Hide manifest from folder3
        }

        @Test
        void testIsFiltered() throws IOException {
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2, filter2),
                    new FilteredPath(path3, filter3)))) {
                assertThat(filtered).isInstanceOf(CompositeJarContents.class);
                assertTrue(((CompositeJarContents) filtered).isFiltered());
            }

            // Without filters
            try (var unfiltered = JarContents.ofPaths(List.of(path1, path2))) {
                assertThat(unfiltered).isInstanceOf(CompositeJarContents.class);
                assertFalse(((CompositeJarContents) unfiltered).isFiltered());
            }
        }

        @Test
        void testFilteredContainsFile() throws IOException {
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2, filter2),
                    new FilteredPath(path3, filter3)))) {
                // file1.txt is filtered out from folder1
                assertFalse(filtered.containsFile("file1.txt"));

                // Other files are still accessible
                assertTrue(filtered.containsFile("file2.txt"));
                assertTrue(filtered.containsFile("file3.txt"));
            }
        }

        @Test
        void testFilteredGet() throws IOException {
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2, filter2),
                    new FilteredPath(path3, filter3)))) {
                // file1.txt is filtered out
                assertNull(filtered.get("file1.txt"));

                // subdir files are filtered out from folder2
                assertNull(filtered.get("subdir/nested.txt"));
            }
        }

        @Test
        void testFilteredManifest() throws IOException {
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2, filter2),
                    new FilteredPath(path3, filter3)))) {
                // Manifest from folder3 is filtered, should fall back to folder1
                Manifest manifest = filtered.getManifest();
                assertNotNull(manifest);
                assertEquals("folder1", manifest.getMainAttributes().getValue("From"));
            }
        }

        @Test
        void testFilteredVisitContent() throws IOException {
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2, filter2),
                    new FilteredPath(path3, filter3)))) {
                var visited = new ArrayList<String>();
                filtered.visitContent("", (relativePath, resource) -> visited.add(relativePath));

                // Should not include filtered files
                assertThat(visited)
                        .doesNotContain("file1.txt", "subdir/nested.txt")
                        .contains("file2.txt", "file3.txt", "shared.txt");
            }
        }

        @Test
        void testFilteredChecksum() throws IOException {
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2, filter2),
                    new FilteredPath(path3, filter3)))) {
                // Filtered composites don't have checksums
                assertThat(filtered.getChecksum()).isEmpty();
            }
        }

        @Test
        void testPartialFilters() throws IOException {
            // Test with some paths filtered and some not
            try (var filtered = JarContents.ofFilteredPaths(List.of(
                    new FilteredPath(path1, filter1),
                    new FilteredPath(path2), // No filter
                    new FilteredPath(path3, filter3)))) {
                assertThat(filtered).isInstanceOf(CompositeJarContents.class);
                assertTrue(((CompositeJarContents) filtered).isFiltered());

                // file1.txt is filtered from path1
                assertFalse(filtered.containsFile("file1.txt"));

                // subdir files from path2 are NOT filtered
                assertTrue(filtered.containsFile("subdir/nested.txt"));
            }
        }
    }

    @Nested
    class ChecksumTests {
        @Test
        void testChecksumWithFolders() throws IOException {
            // Folders don't have checksums, so composite shouldn't either
            try (var composite = JarContents.ofPaths(List.of(path1, path2))) {
                assertThat(composite.getChecksum()).isEmpty();
            }
        }

        @Test
        void testChecksumCaching() throws IOException {
            // Create jar contents with checksums
            var jarPath1 = createJarWithChecksum("jar1");
            var jarPath2 = createJarWithChecksum("jar2");

            try (var composite = JarContents.ofPaths(List.of(jarPath1, jarPath2))) {
                // Call twice to test caching
                var checksum1 = composite.getChecksum();
                var checksum2 = composite.getChecksum();

                assertThat(checksum1).isPresent();
                assertThat(checksum2).isPresent();
                assertEquals(checksum1.get(), checksum2.get());
            }
        }

        private Path createJarWithChecksum(String name) throws IOException {
            writeTextFile(name + "/test.txt", "content");
            return makeJar(name);
        }
    }
}
