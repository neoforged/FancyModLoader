/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.jarcontents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;
import net.neoforged.fml.util.PathPrettyPrinting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JarFileContentsTest extends AbstractJarContentsTest {
    JarContents contents;

    Path jarFilePath;

    private JarContents getJarContents() throws IOException {
        return getJarContents(manifest -> {});
    }

    private JarContents getJarContents(Consumer<Manifest> customizer) throws IOException {
        jarFilePath = makeJar(customizer);
        return contents = JarContents.ofPath(jarFilePath);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (contents != null) {
            contents.close();
        }
    }

    @Test
    void testGetContentRoots() throws IOException {
        assertThat(getJarContents().getContentRoots()).containsOnly(jarFilePath);
    }

    @Test
    void testGetNonExistentResource() throws IOException {
        assertNull(getJarContents().get("some/path"));
    }

    @Test
    void testGetForFolder() throws IOException {
        Files.createDirectory(tempDir.resolve("folder"));
        assertNull(getJarContents().get("folder"));
    }

    @Test
    void testPrimaryPathIsJarPath() throws IOException {
        JarContents jarContents = getJarContents();
        assertEquals(jarFilePath, jarContents.getPrimaryPath());
    }

    @Test
    void testNoChecksum() throws IOException {
        // Expect SHA265 checksum
        var checksum = getJarContents().getChecksum();

        var expected = Hashing.sha256().hashBytes(Files.readAllBytes(jarFilePath)).toString();
        assertThat(checksum).contains(expected);
    }

    @Test
    void testFindFile() throws Exception {
        writeTextFile("somefile", "somefile");
        URI uri = getJarContents().findFile("somefile").orElse(null);
        assertNotNull(uri);

        // Try to verify it's actually a valid URI that can be opened.
        var connection = uri.toURL().openConnection();
        assertThat(connection).isInstanceOfSatisfying(JarURLConnection.class, jarURLConnection -> {
            try {
                assertEquals(jarFilePath.toUri().toURL(), jarURLConnection.getJarFileURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            assertEquals("somefile", jarURLConnection.getEntryName());
        });
    }

    @Test
    void testFindFileForDirectoriesReturnsEmpty() throws Exception {
        Files.createDirectories(tempDir.resolve("dir"));
        assertThat(getJarContents().findFile("dir")).isEmpty();
    }

    @Test
    void testFindFileForMissingFileReturnsEmpty() throws IOException {
        assertThat(getJarContents().findFile("missing")).isEmpty();
    }

    @Nested
    class GetJarResource {
        JarResource resource;

        @BeforeEach
        void setUp() throws IOException {
            Files.createDirectories(tempDir.resolve("folder"));
            Files.writeString(tempDir.resolve("folder/file"), "hello world");
            resource = getJarContents().get("folder/file");
        }

        @Test
        void testResourceExists() {
            assertNotNull(resource);
        }

        @Test
        void testOpen() throws IOException {
            try (var stream = resource.open()) {
                assertThat(stream.readAllBytes()).isEqualTo("hello world".getBytes());
            }
        }

        @Test
        void testAttributes() throws IOException {
            var onDiskAttributes = Files.readAttributes(tempDir.resolve("folder/file"), BasicFileAttributes.class);
            var jarAttributes = resource.attributes();
            // ZIP only supports second-level precision, so we need to truncate the on-disk time to seconds
            long onDiskMillis = onDiskAttributes.lastModifiedTime().toMillis();
            long truncatedMillis = (onDiskMillis / 1000) * 1000;
            FileTime expected = FileTime.fromMillis(truncatedMillis);
            assertEquals(expected, jarAttributes.lastModified());
            assertEquals(onDiskAttributes.size(), jarAttributes.size());
        }

        @Test
        void testRetain() {
            // For resources not obtained from visit, this should be identical
            assertSame(resource, resource.retain());
        }
    }

    @Test
    void testOpenFileForMissingFile() throws IOException {
        assertNull(getJarContents().openFile("file"));
    }

    @Test
    void testOpenFileForFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));

        assertThrows(IOException.class, () -> {
            try (var stream = getJarContents().openFile("folder")) {
                stream.read();
            }
        });
    }

    @Test
    void testOpenFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        try (var stream = getJarContents().openFile("folder/file")) {
            assertNotNull(stream);
            assertThat(stream.readAllBytes()).isEqualTo("hello world".getBytes());
        }
    }

    @Test
    void testContainsFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        assertTrue(getJarContents().containsFile("folder/file"));
    }

    @Test
    void testContainsFileForMissingFile() throws IOException {
        assertFalse(getJarContents().containsFile("missing_file"));
    }

    @Test
    void testContainsFileForFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        assertFalse(getJarContents().containsFile("folder"));
    }

    @Test
    void testReadFileForMissingFile() throws IOException {
        assertNull(getJarContents().readFile("file"));
    }

    @Test
    void testReadFileForFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));

        assertThrows(IOException.class, () -> getJarContents().readFile("folder"));
    }

    @Test
    void testReadFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        var bytes = getJarContents().readFile("folder/file");
        assertNotNull(bytes);
        assertThat(bytes).isEqualTo("hello world".getBytes());
    }

    @Test
    void testGetManifest() throws IOException {
        Manifest manifest = getJarContents(m -> {
            m.getMainAttributes().putValue("Created-By", "Test");
        }).getManifest();
        assertNotNull(manifest);
        assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("Test", manifest.getMainAttributes().getValue("Created-By"));
    }

    @Test
    void testGetManifestReturnsEmptyManifestIfManifestIsMissing() throws IOException {
        // Create an empty ZIP that assuredly has no jar manifest
        Path emptyZip = tempDir.resolve("emptyzip.zip");
        new ZipOutputStream(Files.newOutputStream(emptyZip)).close();

        try (var content = JarContents.ofPath(emptyZip)) {
            // JarFile will return null if there's no manifest at all
            Manifest manifest = content.getManifest();
            assertNotNull(manifest);
            assertThat(manifest.getMainAttributes()).isEmpty();
        }
    }

    @Test
    void testToString() throws IOException {
        JarContents jarContents = getJarContents();
        assertEquals("jar(" + PathPrettyPrinting.prettyPrint(jarFilePath) + ")", jarContents.toString());
    }

    /**
     * Tests multi-release jar behavior.
     */
    @Nested
    class MultiReleaseJar {
        @AutoClose
        JarContents contents;

        @BeforeEach
        void setUp() throws IOException {
            writeTextFile("META-INF/versions/9/folder/file", "hello world");

            var tempJar = makeJar(manifest -> {
                manifest.getMainAttributes().putValue("Multi-Release", "true");
            });

            contents = JarContents.ofPath(tempJar);
        }

        @Test
        void testContainsFile() {
            assertTrue(contents.containsFile("folder/file"));
        }

        @Test
        void testOpenFile() throws IOException {
            try (var stream = contents.openFile("folder/file")) {
                assertNotNull(stream);
                assertThat(stream.readAllBytes()).isEqualTo("hello world".getBytes());
            }
        }

        @Test
        void testReadFile() throws IOException {
            var bytes = contents.readFile("folder/file");
            assertNotNull(bytes);
            assertThat(bytes).isEqualTo("hello world".getBytes());
        }
    }

    @Nested
    class VisitContent {
        @BeforeEach
        void setUp() throws IOException {
            Files.createDirectories(tempDir.resolve("empty_folder"));
            writeTextFile("folder/file1", "folder/file1");
            writeTextFile("folder/file2", "folder/file2");
            writeTextFile("folder2/subfolder/file", "folder2/subfolder/file");
            writeTextFile("root_file", "root_file");
        }

        @Test
        void testVisitFromRoot() throws IOException {
            var visitor = new CollectingVisitor();
            getJarContents().visitContent(visitor);

            assertThat(visitor.visited).containsOnly("META-INF/MANIFEST.MF", "folder/file1", "folder/file2", "folder2/subfolder/file", "root_file");
        }

        @Test
        void testVisitFromSubfolder() throws IOException {
            var visitor = new CollectingVisitor();
            getJarContents().visitContent("folder", visitor);

            assertThat(visitor.visited).containsOnly("folder/file1", "folder/file2");
        }

        @Test
        void testVisitStartingFromFile() throws IOException {
            var visitor = new CollectingVisitor();
            getJarContents().visitContent("folder/file1", visitor);
            // When the starting folder is not a directory, nothing is returned
            assertThat(visitor.visited).isEmpty();
        }

        @Test
        void testVisitFromNonExistentFolder() throws IOException {
            var visitor = new CollectingVisitor();
            getJarContents().visitContent("does_not_exist", visitor);
            assertThat(visitor.visited).isEmpty();
        }

        @Test
        void testJarContentRetain() throws IOException {
            var resources = new ArrayList<JarResource>();
            getJarContents().visitContent("folder", (relativePath, resource) -> resources.add(resource.retain()));
            assertThat(resources).hasSize(2);

            assertThat(resources)
                    .extracting(resource -> new String(resource.readAllBytes()))
                    .containsOnly("folder/file1", "folder/file2");
        }

        static class CollectingVisitor implements JarResourceVisitor {
            List<String> visited = new ArrayList<>();

            @Override
            public void visit(String relativePath, JarResource resource) {
                try {
                    if (!"META-INF/MANIFEST.MF".equals(relativePath)) {
                        var content = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                        assertEquals(content, relativePath);
                    }
                    visited.add(relativePath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
