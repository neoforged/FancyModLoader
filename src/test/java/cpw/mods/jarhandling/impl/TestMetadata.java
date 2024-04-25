package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;

public class TestMetadata
{
    @Test
    void testMavenJar() {
        var path = Paths.get("startofthepathchain/new-protected-class-1.16.5/1.1_mapped_official_1.17.1/new-protected-class-1.16.5-1.1_mapped_official_1.17.1-api.jar");
        var meta = JarMetadata.from(new FakeJarContent(path));
        Assertions.assertEquals("_new._protected._class._1._16._5", meta.name());
        Assertions.assertEquals("1.1_mapped_official_1.17.1", meta.version());
    }
    
    @Test
    void testRootStart() {
        var path = Paths.get("/instance/mods/1life-1.5.jar");
        var meta = JarMetadata.from(new FakeJarContent(path));
        Assertions.assertEquals("_1life", meta.name());
        Assertions.assertEquals("1.5", meta.version());
    }

    @Test
    void testNumberStart() {
        var path = Paths.get("mods/1life-1.5.jar");
        var meta = JarMetadata.from(new FakeJarContent(path));
        Assertions.assertEquals("_1life", meta.name());
        Assertions.assertEquals("1.5", meta.version());
    }

    record FakeJarContent(Path primaryPath) implements JarContents {
        @Override
        public Path getPrimaryPath() {
            return primaryPath;
        }

        @Override
        public Optional<URI> findFile(String name) {
            return Optional.empty();
        }

        @Override
        public Manifest getManifest() {
            return new Manifest();
        }

        @Override
        public Set<String> getPackages() {
            return Set.of();
        }

        @Override
        public Set<String> getPackagesExcluding(String... excludedRootPackages) {
            return Set.of();
        }

        @Override
        public List<SecureJar.Provider> getMetaInfServices() {
            return List.of();
        }
    }
}
