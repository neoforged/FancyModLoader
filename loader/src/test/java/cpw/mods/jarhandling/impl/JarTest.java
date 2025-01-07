package cpw.mods.jarhandling.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class JarTest {
    @Test
    void testNotJar() throws Exception {
        var path = Paths.get("build");
        var jar = Jar.of(path);
        assertNull(jar.moduleDataProvider().getManifest());
    }

    @Test
    void testNonExistent() throws Exception {
        var path = Paths.get("thisdoesnotexist");
        assertThrows(IOException.class, () -> Jar.of(path), "File does not exist");
    }
}
