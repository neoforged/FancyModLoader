package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMetadata {
    @Test
    void testMavenJar() throws IOException {
        var path = Paths.get("startofthepathchain/new-protected-class-1.16.5/1.1_mapped_official_1.17.1/new-protected-class-1.16.5-1.1_mapped_official_1.17.1-api.jar");
        var meta = JarMetadata.from(JarContents.empty(path));
        Assertions.assertEquals("_new._protected._class._1._16._5", meta.name());
        Assertions.assertEquals("1.1_mapped_official_1.17.1", meta.version());
    }

    @Test
    void testRootStart() throws IOException {
        var path = Paths.get("/instance/mods/1life-1.5.jar");
        var meta = JarMetadata.from(JarContents.empty(path));
        Assertions.assertEquals("_1life", meta.name());
        Assertions.assertEquals("1.5", meta.version());
    }

    @Test
    void testNumberStart() throws IOException {
        var path = Paths.get("mods/1life-1.5.jar");
        var meta = JarMetadata.from(JarContents.empty(path));
        Assertions.assertEquals("_1life", meta.name());
        Assertions.assertEquals("1.5", meta.version());
    }
}
