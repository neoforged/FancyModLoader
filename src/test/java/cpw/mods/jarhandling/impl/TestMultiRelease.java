package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.SecureJar;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestMultiRelease {
    @Test
    public void testMultiRelease() {
        Path rootDir = Paths.get("src", "test", "resources", "multirelease");
        var jar = SecureJar.from(rootDir);

        var aContents = readString(jar, "a.txt");
        // Should be overridden by the Java 9 version
        Assertions.assertEquals("new", aContents.strip());
        // Java 1000 override should not be loaded
        Assertions.assertNotEquals("too new", aContents.strip());

        var bContents = readString(jar, "b.txt");
        // No override
        Assertions.assertEquals("old", bContents.strip());
        // In particular, Java 1000 override should not be used
        Assertions.assertNotEquals("too new", bContents.strip());
    }

    private static String readString(SecureJar jar, String file) {
        // Note: we must read the jar through the module data provider for version-specific files to be used
        try (var is = jar.moduleDataProvider().open(file).get()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
