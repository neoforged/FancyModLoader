package cpw.mods.jarhandling.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContents.FilteredPath;
import cpw.mods.jarhandling.JarResource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CompositeJarContentsTest extends AbstractJarContentsTest {
    private Path path1;
    private Path path2;
    private Path path3;
    private JarContents delegate1;
    private JarContents delegate2;
    private JarContents delegate3;
    // Maps from relative path to the delegate we expect to contain it
    private Map<String, JarContents> relativePathToExpectedDelegate;

    @BeforeEach
    void setUp() throws IOException {
        // Set up three folders with overlapping and unique files
        path1 = tempDir.resolve("folder1");
        path2 = tempDir.resolve("folder2");
        path3 = tempDir.resolve("folder3");

        // Folder 1 contents
        writeTextFile("folder1/file1.txt", "folder1");
        writeTextFile("folder1/shared.txt", "folder1");
        writeTextFile("folder1/subdir/shared.txt", "folder1");
        writeTextFile("folder1/subdir/file1.txt", "folder1");
        writeTextFile("folder1/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFrom: folder1\n");

        // Folder 2 contents
        writeTextFile("folder2/file2.txt", "folder2");
        writeTextFile("folder2/shared.txt", "folder2");
        writeTextFile("folder2/subdir/shared.txt", "folder2");
        writeTextFile("folder2/subdir/file2.txt", "folder2");

        // Folder 3 contents
        writeTextFile("folder3/file3.txt", "folder3");
        writeTextFile("folder3/shared.txt", "folder3");
        writeTextFile("folder3/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFrom: folder3\n");

        delegate1 = JarContents.ofPath(path1);
        delegate2 = JarContents.ofPath(path2);
        delegate3 = JarContents.ofPath(path3);

        relativePathToExpectedDelegate = Map.of(
                "file1.txt", delegate1,
                "file2.txt", delegate2,
                "file3.txt", delegate3,
                "subdir/shared.txt", delegate2,
                "subdir/file1.txt", delegate1,
                "subdir/file2.txt", delegate2,
                "shared.txt", delegate3,
                "META-INF/MANIFEST.MF", delegate3);
    }

    @AfterEach
    void tearDown() throws IOException {
        delegate1.close();
        delegate2.close();
        delegate3.close();
    }

    @Test
    void testCannotCreateAnEmptyComposite() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeJarContents(List.of()));
    }

    @Test
    void testCannotCreateACompositeWithLessThanTwoDelegatesAndNoFilter() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new CompositeJarContents(List.of(delegate1), Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> new CompositeJarContents(List.of(delegate1)));
        // This checks that constructing it with a single path and a filter is allowed
        new CompositeJarContents(List.of(delegate1), List.of(p -> true)).close();
    }

    @Test
    void testCannotCreateACompositeWithMismatchedDelegatesAndFilters() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeJarContents(List.of(delegate1, delegate2), List.of(relativePath -> true)));
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
    void testGet() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            for (var entry : relativePathToExpectedDelegate.entrySet()) {
                var resource = composite.get(entry.getKey());
                assertContentMatches(entry, resource.readAllBytes());
            }
            assertNull(composite.get("nonexistent.txt"), "Expected nonexistent.txt to be missing");
        }
    }

    @Test
    void testReadFile() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            for (var entry : relativePathToExpectedDelegate.entrySet()) {
                var content = composite.readFile(entry.getKey());
                assertContentMatches(entry, content);
            }
            assertNull(composite.readFile("nonexistent.txt"), "Expected nonexistent.txt to be missing");
        }
    }

    @Test
    void testOpenFile() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            for (var entry : relativePathToExpectedDelegate.entrySet()) {
                try (var content = composite.openFile(entry.getKey())) {
                    assertNotNull(content, "Expected to find " + entry.getKey());
                    assertContentMatches(entry, content.readAllBytes());
                }
            }
            assertNull(composite.openFile("nonexistent.txt"), "Expected nonexistent.txt to be missing");
        }
    }

    @Test
    void testContainsFile() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            for (var entry : relativePathToExpectedDelegate.entrySet()) {
                assertTrue(composite.containsFile(entry.getKey()), "Expected to find " + entry.getKey());
            }
            assertFalse(composite.containsFile("nonexistent.txt"), "Expected nonexistent.txt to be missing");
        }
    }

    @Test
    void testFindFile() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            for (var entry : relativePathToExpectedDelegate.entrySet()) {
                var expectedUri = entry.getValue().findFile(entry.getKey());
                assertEquals(expectedUri, composite.findFile(entry.getKey()), "Mismatched URI for " + entry.getKey());
            }
            assertNotNull(composite.findFile("nonexistent.txt"), "Expected nonexistent.txt to be missing");
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
        // If later delegates don't have a manifest, should fall back to earlier ones
        try (var composite = new CompositeJarContents(List.of(JarContents.ofPath(path1), new EmptyJarContents(Path.of(""))))) {
            Manifest manifest = composite.getManifest();

            assertNotNull(manifest);
            assertEquals("folder1", manifest.getMainAttributes().getValue("From"));
        }
    }

    @Test
    void testGetManifestWhenNoDelegateHasAManifest() throws IOException {
        try (var composite = new CompositeJarContents(List.of(new EmptyJarContents(Path.of("")), new EmptyJarContents(Path.of(""))))) {
            assertSame(EmptyManifest.INSTANCE, composite.getManifest());
        }
    }

    @Test
    void testVisitContent() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            var visited = new HashMap<String, JarResource>();
            composite.visitContent("", (relativePath, resource) -> visited.put(relativePath, resource.retain()));

            // Should visit all unique files, and shared files only once
            assertThat(visited.keySet()).containsExactlyInAnyOrderElementsOf(relativePathToExpectedDelegate.keySet());

            // Check that the shared files are from the right delegate
            for (var entry : relativePathToExpectedDelegate.entrySet()) {
                assertContentMatches(entry, visited.get(entry.getKey()).readAllBytes());
            }
        }
    }

    @Test
    void testVisitContentFromSubfolder() throws IOException {
        try (var composite = JarContents.ofPaths(List.of(path1, path2, path3))) {
            var visited = new ArrayList<String>();

            composite.visitContent("subdir", (relativePath, resource) -> visited.add(relativePath));

            assertThat(visited).containsExactlyInAnyOrder(
                    "subdir/file1.txt",
                    "subdir/file2.txt",
                    "subdir/shared.txt");
        }
    }

    @Test
    void testClose() throws IOException {
        delegate1 = Mockito.spy(delegate1);
        delegate2 = Mockito.spy(delegate2);
        new CompositeJarContents(List.of(delegate1, delegate2)).close();
        verify(delegate1).close();
        verify(delegate2).close();
    }

    @Test
    void testToString() throws IOException {
        var filters = new ArrayList<JarContents.PathFilter>();
        filters.add(null);
        filters.add(path -> true); // Dummy filter

        try (var composite = new CompositeJarContents(List.of(delegate1, delegate2), filters)) {
            assertEquals("composite(" + delegate1 + ", filtered(" + delegate2 + "))", composite.toString());
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
                assertTrue(filtered.containsFile("subdir/shared.txt"));
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

    private static void assertContentMatches(Map.Entry<String, JarContents> entry, byte @Nullable [] actual) throws IOException {
        assertNotNull(actual, "Expected to find " + entry.getKey());

        var expected = entry.getValue().readFile(entry.getKey());
        assertNotNull(expected, "relativePathToExpectedDelegate is invalid for " + entry.getKey());
        assertEquals(new String(expected), new String(actual));
    }
}
