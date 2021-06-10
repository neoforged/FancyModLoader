package cpw.mods.niofs.union;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;

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

    }
}
