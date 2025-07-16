package cpw.mods.jarhandling.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarResource;
import cpw.mods.jarhandling.JarResourceVisitor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarContentsTest {
    @TempDir
    Path tempDir;

    JarContents contents;

    @BeforeEach
    void setUp() {
        contents = JarContents.of(tempDir);
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
        void testRetain() throws IOException {
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

        assertNull(contents.openFile("folder"));
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

        assertNull(contents.readFile("folder"));
    }

    @Test
    void testReadFile() throws IOException {
        Files.createDirectories(tempDir.resolve("folder"));
        Files.writeString(tempDir.resolve("folder/file"), "hello world");

        var bytes = contents.readFile("folder/file");
        assertNotNull(bytes);
        assertThat(bytes).isEqualTo("hello world".getBytes());
    }

    @Nested
    class MultiReleaseJar {
        JarContents contents;

        @BeforeEach
        void setUp() throws IOException {
            contents = getMultiReleaseJar();
        }

        @Test
        void testContainsFile() throws IOException {
            var contents = getMultiReleaseJar();

            assertTrue(contents.containsFile("folder/file"));
        }

        @Test
        void testOpenFile() throws IOException {
            var contents = getMultiReleaseJar();

            try (var stream = contents.openFile("folder/file")) {
                assertNotNull(stream);
                assertThat(stream.readAllBytes()).isEqualTo("hello world".getBytes());
            }
        }

        @Test
        void testReadFile() throws IOException {
            var contents = getMultiReleaseJar();

            var bytes = contents.readFile("folder/file");
            assertNotNull(bytes);
            assertThat(bytes).isEqualTo("hello world".getBytes());
        }

        private JarContents getMultiReleaseJar() throws IOException {
            Manifest mf = new Manifest();
            mf.getMainAttributes().putValue("Manifest-Version", "1.0");
            mf.getMainAttributes().putValue("Multi-Release", "true");
            Files.createDirectories(tempDir.resolve("META-INF"));
            try (var out = Files.newOutputStream(tempDir.resolve("META-INF/MANIFEST.MF"))) {
                mf.write(out);
            }
            Files.createDirectories(tempDir.resolve("META-INF/versions/9/folder"));
            Files.writeString(tempDir.resolve("META-INF/versions/9/folder/file"), "hello world");

            return JarContents.of(tempDir); // Multi-version info is only read in the ctor so we have to re-create the jar content after writing it
        }
    }

    @Nested
    class VisitContent {
        @BeforeEach
        void setUp() throws IOException {
            Files.createDirectories(tempDir.resolve("empty_folder"));
            Files.createDirectories(tempDir.resolve("folder"));
            Files.writeString(tempDir.resolve("folder/file1"), "folder/file1");
            Files.writeString(tempDir.resolve("folder/file2"), "folder/file2");
            Files.createDirectories(tempDir.resolve("folder2/subfolder"));
            Files.writeString(tempDir.resolve("folder2/subfolder/file"), "folder2/subfolder/file");
            Files.writeString(tempDir.resolve("root_file"), "root_file");
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
