package cpw.mods.jarhandling.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.jarhandling.SecureJar;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestSecureJarLoading {
    @BeforeAll
    static void setup() {
        //System.setProperty("securejarhandler.debugVerifier", "true");
        System.setProperty("securejarhandler.useUnsafeAccessor", "true");
    }

    @Test
    void testNotJar() throws Exception {
        final var path = Paths.get("build");
        SecureJar jar = Jar.of(path);
        assertAll(
                () -> assertTrue(jar.moduleDataProvider().getManifest().getMainAttributes().isEmpty(), "Empty manifest returned"));
    }

    @Test
    void testNonExistent() throws Exception {
        final var path = Paths.get("thisdoesnotexist");
        assertThrows(UncheckedIOException.class, () -> Jar.of(path), "File does not exist");
    }
}
