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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import net.neoforged.fml.util.PathPrettyPrinting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FolderJarContentsTest extends AbstractJarContentsTest {
    JarContents contents;

    @BeforeEach
    void setUp() throws IOException {
        contents = JarContents.ofPath(tempDir);
    }

    @Test
    void testGetContentRoots() {
        assertThat(contents.getContentRoots()).containsOnly(tempDir);
    }

    @Test
    void testGetNonExistentResource() {
        assertNull(contents.get("some/path"));
    }

    @Test
    void testGetForFolder() throws IOException {
        Files.createDirectory(tempDir.resolve("folder"));
        assertNull(contents.get("folder"));
    }

    @Test
    void testPrimaryPathIsFolderPath() {
        assertEquals(tempDir, contents.getPrimaryPath());
    }

    @Test
    void testNoChecksum() {
        // Folders do not have a meaningful checksum
        assertThat(contents.getChecksum()).isEmpty();
    }

    @Test
    void testFindFileGivesFilesystemUri() throws Exception {
        var path = writeTextFile("somefile", "somefile");
        assertThat(contents.findFile("somefile")).contains(path.toUri());
    }

    @Test
    void testFindFileForDirectoriesReturnsEmpty() throws Exception {
        Files.createDirectories(tempDir.resolve("dir"));
        assertThat(contents.findFile("dir")).isEmpty();
    }

    @Test
    void testFindFileForMissingFileReturnsEmpty() {
        assertThat(contents.findFile("missing")).isEmpty();
    }

    @Nested
    class GetJarResource {
        JarResource resource;

        @BeforeEach
        void setUp() throws IOException {
            Files.createDirectories(tempDir.resolve("folder"));
            Files.writeString(tempDir.resolve("folder/file"), "hello world");
            resource = contents.get("folder/file");
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
            assertEquals(onDiskAttributes.lastModifiedTime(), jarAttributes.lastModified());
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
        assertNull(contents.openFile("file"));
    }

    @Test
    void testOpenFileForFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));

        assertThrows(IOException.class, () -> {
            try (var stream = contents.openFile("folder")) {
                stream.read();
            }
        });
    }

    @Test
    void testOpenFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        try (var stream = contents.openFile("folder/file")) {
            assertNotNull(stream);
            assertThat(stream.readAllBytes()).isEqualTo("hello world".getBytes());
        }
    }

    @Test
    void testContainsFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        assertTrue(contents.containsFile("folder/file"));
    }

    @Test
    void testContainsFileForMissingFile() {
        assertFalse(contents.containsFile("missing_file"));
    }

    @Test
    void testContainsFileForFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        assertFalse(contents.containsFile("folder"));
    }

    @Test
    void testReadFileForMissingFile() throws IOException {
        assertNull(contents.readFile("file"));
    }

    @Test
    void testReadFileForFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));

        assertThrows(IOException.class, () -> contents.readFile("folder"));
    }

    @Test
    void testReadFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        var bytes = contents.readFile("folder/file");
        assertNotNull(bytes);
        assertThat(bytes).isEqualTo("hello world".getBytes());
    }

    @Test
    void testGetManifest() throws IOException {
        writeTextFile("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nCreated-By: Test\n\n");
        Manifest manifest = contents.getManifest();
        assertNotNull(manifest);
        assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("Test", manifest.getMainAttributes().getValue("Created-By"));
    }

    @Test
    void testGetManifestReturnsEmptyManifestIfManifestIsMissing() {
        Manifest manifest = contents.getManifest();
        assertSame(EmptyManifest.INSTANCE, manifest);
    }

    @Test
    void testToString() {
        assertEquals("folder(" + PathPrettyPrinting.prettyPrint(tempDir) + ")", contents.toString());
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
        void testVisitFromRoot() {
            var visitor = new CollectingVisitor();
            contents.visitContent(visitor);

            assertThat(visitor.visited).containsOnly("folder/file1", "folder/file2", "folder2/subfolder/file", "root_file");
        }

        @Test
        void testVisitFromSubfolder() {
            var visitor = new CollectingVisitor();
            contents.visitContent("folder", visitor);

            assertThat(visitor.visited).containsOnly("folder/file1", "folder/file2");
        }

        @Test
        void testVisitStartingFromFile() {
            var visitor = new CollectingVisitor();
            contents.visitContent("folder/file1", visitor);
            // When the starting folder is not a directory, nothing is returned
            assertThat(visitor.visited).isEmpty();
        }

        @Test
        void testVisitFromNonExistentFolder() {
            var visitor = new CollectingVisitor();
            contents.visitContent("does_not_exist", visitor);
            assertThat(visitor.visited).isEmpty();
        }

        @Test
        void testJarContentRetain() {
            var resources = new ArrayList<JarResource>();
            contents.visitContent("folder", (relativePath, resource) -> resources.add(resource.retain()));
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
                    var content = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                    assertEquals(content, relativePath);
                    visited.add(relativePath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
