package cpw.mods.jarhandling.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.neoforged.fml.testlib.ModFileBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JarMetadataTest {
    @TempDir
    Path tempDir;

    @Nested
    class WithModuleInfo {
        @Test
        void testSimpleNamedModule() throws IOException {
            var metadata = getJarMetadata(ModuleDescriptor.newModule("test_module").version("1.0").build());

            assertEquals("test_module", metadata.name());
            assertEquals("1.0", metadata.version());
            var descriptor = metadata.descriptor();
            assertEquals("test_module", descriptor.name());
            assertEquals(Optional.of("1.0"), descriptor.rawVersion());

            assertEquals(Set.of(ModuleDescriptor.Modifier.OPEN), descriptor.modifiers(), "All modules should be opened automatically.");
        }
    }

    @Nested
    class NonModularWithoutAutomaticModuleName {
        @Test
        void testMavenJar() throws IOException {
            var path = "startofthepathchain/new-protected-class-1.16.5/1.1_mapped_official_1.17.1/new-protected-class-1.16.5-1.1_mapped_official_1.17.1-api.jar";
            var meta = getJarMetadata(path, builder -> {});
            assertEquals("_new._protected._class._1._16._5", meta.name());
            assertEquals("1.1_mapped_official_1.17.1", meta.version());
        }

        @Test
        void testNumberStart() throws IOException {
            var path = "mods/1life-1.5.jar";
            var meta = getJarMetadata(path, builder -> {});
            assertEquals("_1life", meta.name());
            assertEquals("1.5", meta.version());
        }
    }

    @Nested
    class NonModularWithAutomaticModuleName {
        @Test
        void testAutomaticModuleName() throws IOException {
            var meta = getJarMetadata("test.jar", builder -> {
                builder.withManifest(Map.of(
                        "Automatic-Module-Name", "helloworld"));
            });
            assertEquals("helloworld", meta.name());
            assertNull(meta.version());
        }
    }

    // Compute JarMetadata for a Jar that only contains a module-info.class with the given descriptor.
    private JarMetadata getJarMetadata(ModuleDescriptor descriptor) throws IOException {
        return getJarMetadata("test.jar", b -> b.withModuleInfo(descriptor));
    }

    private JarMetadata getJarMetadata(String path, ModFileCustomizer consumer) throws IOException {
        var testJar = tempDir.resolve(path);

        Files.createDirectories(testJar.getParent());

        var builder = new ModFileBuilder(testJar);
        try {
            consumer.customize(builder);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        builder.build();

        try (var jc = JarContents.of(testJar)) {
            var metadata = JarMetadata.from(jc);
            metadata.descriptor(); // This causes the packages to be scanned so we can close the unionfs
            return metadata;
        }
    }

    @FunctionalInterface
    interface ModFileCustomizer {
        void customize(ModFileBuilder builder) throws IOException;
    }
}
