package cpw.mods.jarhandling.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cpw.mods.jarhandling.JarResourceVisitor;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmptyJarContentsTest {
    private EmptyJarContents emptyJar;
    private Path testPath;

    @BeforeEach
    void setUp() {
        testPath = Path.of("this/does/not/need/to/exist");
        emptyJar = new EmptyJarContents(testPath);
    }

    @Test
    void testGetChecksum() {
        // An empty jar has no meaningful checksum
        assertThat(emptyJar.getChecksum()).isEmpty();
    }

    @Test
    void testFindFile() {
        assertThat(emptyJar.findFile("any/path/file.txt")).isEmpty();
        assertThat(emptyJar.findFile("")).isEmpty();
        assertThat(emptyJar.findFile("/")).isEmpty();
    }

    @Test
    void testGet() {
        assertNull(emptyJar.get("any/path/file.txt"));
        assertNull(emptyJar.get(""));
        assertNull(emptyJar.get("/"));
    }

    @Test
    void testContainsFile() {
        assertFalse(emptyJar.containsFile("any/path/file.txt"));
        assertFalse(emptyJar.containsFile(""));
        assertFalse(emptyJar.containsFile("/"));
    }

    @Test
    void testGetPrimaryPath() {
        assertEquals(testPath, emptyJar.getPrimaryPath());
    }

    @Test
    void testGetContentRoots() {
        // Somewhat counterintuitive, but an empty jar has a primary path, but no content roots,
        // Since the primary path really just serves as an identifier of sorts and does not have to exist.
        assertThat(emptyJar.getContentRoots()).isEmpty();
    }

    @Test
    void testGetManifest() {
        // Should not allocate a new manifest.
        assertSame(EmptyManifest.INSTANCE, emptyJar.getManifest());
    }

    @Test
    void testOpenFile() {
        assertNull(emptyJar.openFile("any/path/file.txt"));
    }

    @Test
    void testReadFile() {
        assertNull(emptyJar.readFile("any/path/file.txt"));
    }

    @Test
    void testVisitContent() {
        AtomicInteger visitCount = new AtomicInteger(0);
        JarResourceVisitor visitor = (relativePath, jarResource) -> {
            visitCount.incrementAndGet();
        };

        emptyJar.visitContent("", visitor);
        assertThat(visitCount).hasValue(0);

        emptyJar.visitContent("any/folder", visitor);
        assertThat(visitCount).hasValue(0);
    }

    @Test
    void testToString() {
        // Should show its identifying primary path and that it's empty in toString
        assertEquals("empty(" + testPath + ")", emptyJar.toString());
    }
}
