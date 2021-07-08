package cpw.mods.niofs.union;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestUnionFS {
    @Test
    void testUnionFileSystem() throws IOException {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();

        final var fileSystem = FileSystems.newFileSystem(dir1, Map.of("additional", List.of(dir2)));
        assertAll(
                ()->assertTrue(fileSystem instanceof UnionFileSystem),
                ()->assertIterableEquals(fileSystem instanceof UnionFileSystem ufs ? ufs.getBasePaths(): List.of(), List.of(dir2, dir1))
        );
        UnionFileSystem ufs = (UnionFileSystem) fileSystem;
        final var masktest = ufs.getPath("masktest.txt");
        assertAll(
                ()->assertTrue(Files.exists(masktest)),
                ()->assertEquals(Files.readString(masktest), "dir2")
        );
        assertAll(
                Files.walk(masktest.getRoot())
                .map(Files::exists)
                .map(f->()->assertTrue(f))
        );
        var p = ufs.getRoot().resolve("subdir1/masktestd1.txt");
        p.subpath(2, 3);
        var empty = new UnionPath(ufs);
    }

    @Test
    void testRelativize() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();

        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var ufs = fsp.newFileSystem((path, base) -> true, dir1, dir2);
        var p1 = ufs.getPath("path1");
        var p123 = ufs.getPath("path1/path2/path3");
        var p11 = ufs.getPath("path1/path1");
        var p12 = ufs.getPath("path1/path2");
        var p13 = ufs.getPath("path1/path3");
        var p23 = ufs.getPath("path2/path3");
        var p13plus = ufs.getPath("path1/path3");
        assertAll(
                ()->assertEquals("path2/path3", p1.relativize(p123).toString()),
                ()->assertEquals("../..", p123.relativize(p1).toString()),
                ()->assertEquals("path1", p1.relativize(p11).toString()),
                ()->assertEquals("path2", p1.relativize(p12).toString()),
                ()->assertEquals("path3", p1.relativize(p13).toString()),
                ()->assertEquals("../../path1/path1", p23.relativize(p11).toString()),
                ()->assertEquals("../../path1", p123.relativize(p11).toString()),
                ()->assertEquals(0, p13.relativize(p13plus).getNameCount())
        );
    }

    @Test
    void testPathFiltering() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();
        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var ufs = fsp.newFileSystem((path, base)->!path.startsWith("masktest2.txt"), dir1, dir2);
        var t1 = ufs.getPath("masktest.txt");
        var t3 = ufs.getPath("masktest3.txt");
        var t2 = ufs.getPath("masktest2.txt");
        assertTrue(Files.exists(t1));
        assertTrue(Files.exists(t3));
        assertTrue(Files.notExists(t2));
        var sd1 = ufs.getPath("subdir1");
        var sdt1 = sd1.resolve("masktestsd1.txt");
        var walk = new Path[] {ufs.getRoot(), t1, t3, sd1, sdt1};
        assertDoesNotThrow(()-> {
            try (var set = Files.walk(ufs.getRoot())) {
                var paths = set.toArray(Path[]::new);
                assertArrayEquals(walk, paths);
            }
        });
    }
}
