package cpw.mods.jarhandling.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.neoforged.fml.testlib.ModFileBuilder;
import net.neoforged.fml.testlib.ModuleInfoWriter;
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
            var originalDescriptor = ModuleDescriptor.newModule("test_module")
                    .requires(Set.of(), "other_module")
                    .provides("service.Interface", List.of("provider.Class"))
                    .exports("exported.pkg")
                    .opens("opened.pkg", Set.of("opened_to"))
                    .packages(Set.of("pkg1", "pkg1.other"))
                    .uses("other.Service")
                    .version("1.0").build();
            var metadata = getJarMetadata(originalDescriptor);

            assertEquals("test_module", metadata.name());
            assertEquals("1.0", metadata.version());
            var descriptor = metadata.descriptor();
            assertEquals(originalDescriptor.name(), descriptor.name());
            assertEquals(originalDescriptor.rawVersion(), descriptor.rawVersion());
            assertEquals(originalDescriptor.packages(), descriptor.packages());
            assertEquals(originalDescriptor.exports(), descriptor.exports());
            assertEquals(originalDescriptor.provides(), descriptor.provides());
            assertEquals(originalDescriptor.uses(), descriptor.uses());

            assertEquals(Set.of(ModuleDescriptor.Modifier.OPEN), descriptor.modifiers(), "All modules should be opened automatically.");
            assertEquals(Set.of(), descriptor.opens(), "Open modules has no explicit set of opens");
        }

        @Test
        void testPackagesAreScannedIfNotDeclaredInModuleInfo() throws IOException {
            var descriptor = ModuleDescriptor.newModule("test_module")
                    .version("1.0")
                    .exports("exported_package")
                    .build();
            var testJar = tempDir.resolve("test.jar");
            var metadata = getJarMetadata(testJar, builder -> builder
                    .addBinaryFile("exported_package/SomeClass.class", new byte[] {})
                    .addBinaryFile("somepackage/SomeClass.class", new byte[] {})
                    .addBinaryFile("resources/alsocount/resource.txt", new byte[] {})
                    .addBinaryFile("META-INF/Ignored.class", new byte[] {})
                    .addBinaryFile("not/while/package/Ignored.class", new byte[] {})
                    .addBinaryFile("9/notanidentifier/Ignored.class", new byte[] {})
                    .addBinaryFile("module-info.class", ModuleInfoWriter.toByteArrayWithoutPackages(descriptor)));

            // It should find the package, even if it wasn't declared
            assertEquals(Set.of("somepackage", "exported_package", "resources.alsocount"), metadata.descriptor().packages());

            // Compare against the packages found by the JDK for the same Jar
            var jdkModuleDescriptor = getJdkModuleDescriptor(testJar);
            assertEquals(jdkModuleDescriptor.packages(), metadata.descriptor().packages());
        }

        @Test
        void testPackagesAreNotScannedIfDeclaredInModuleInfo() throws IOException {
            var metadata = getJarMetadata("test.jar", builder -> builder
                    .addBinaryFile("somepackage/SomeClass.class", new byte[] {})
                    .withModuleInfo(ModuleDescriptor.newModule("test_module").version("1.0").packages(Set.of("superpackage")).build()));

            // The physically present "somepackage" is ignored, only the package list from the module descriptor is returned
            assertEquals(Set.of("superpackage"), metadata.descriptor().packages());
        }

        @Test
        void testModuleInfoInMetaInfVersions() throws IOException {
            var moduleInfo = ModuleInfoWriter.toByteArray(ModuleDescriptor.newModule("test_module").version("1.0").build());
            var metadata = getJarMetadata("test.jar", builder -> builder
                    .withManifest(Map.of("Multi-Release", "true"))
                    .addBinaryFile("META-INF/versions/9/module-info.class", moduleInfo));

            assertEquals("test_module", metadata.name());
            assertEquals("1.0", metadata.version());
        }

        // A broken module-info.class shouldn't be ignored
        @Test
        void testCorruptedModuleInfo() {
            assertThrows(Exception.class, () -> getJarMetadata("test.jar", builder -> builder.addTextFile("module-info.class", "JUNK")));
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

        @Test
        void testUnrecognizableVersion() throws IOException {
            var path = "mods/noversion.jar";
            var meta = getJarMetadata(path, builder -> {});
            assertEquals("noversion", meta.name());
            assertNull(meta.version());

            var descriptor = meta.descriptor();
            assertEquals("noversion", descriptor.name());
            assertEquals(Optional.empty(), descriptor.rawVersion());
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

        @Test
        void testServiceProviders() throws Exception {
            var testJar = tempDir.resolve("test.jar");
            var metadata = getJarMetadata(testJar, builder -> builder
                    .addBinaryFile("somepackage/SomeClass.class", new byte[] {})
                    .addService("pkg.SomeService", "somepackage.SomeClass")
                    // This tests that service files with invalid names are ignored
                    .addService("package.Class", "somepackage.SomeClass"));

            var descriptor = metadata.descriptor();
            assertEquals(Set.of("somepackage"), descriptor.packages());

            // Compare against the services found by the JDK for the same Jar
            var jdkModuleDescriptor = getJdkModuleDescriptor(testJar);
            assertEquals(jdkModuleDescriptor.provides(), descriptor.provides());
        }

        @Test
        void testPackageScanning() throws Exception {
            var testJar = tempDir.resolve("test.jar");
            var metadata = getJarMetadata(testJar, builder -> builder
                    .addBinaryFile("exported_package/SomeClass.class", new byte[] {})
                    .addBinaryFile("somepackage/SomeClass.class", new byte[] {})
                    .addBinaryFile("resources/alsocount/resource.txt", new byte[] {})
                    .addBinaryFile("META-INF/Ignored.class", new byte[] {})
                    .addBinaryFile("META-INF/services/subdir/Ignored.class", new byte[] {})
                    .addBinaryFile("not/while/package/Ignored.class", new byte[] {})
                    .addBinaryFile("9/notanidentifier/Ignored.class", new byte[] {}));

            // It should find the package, even if it wasn't declared
            // But unlike normal named modules, automatic modules do *not* declare their resource packages
            // which makes them work like pre-modular Java (resources findable via the ClassLoader).
            assertEquals(Set.of("somepackage", "exported_package"), metadata.descriptor().packages());

            // Compare against the packages found by the JDK for the same Jar
            var jdkModuleDescriptor = getJdkModuleDescriptor(testJar);
            assertEquals(jdkModuleDescriptor.packages(), metadata.descriptor().packages());
        }
    }

    // Compute JarMetadata for a Jar that only contains a module-info.class with the given descriptor.
    private JarMetadata getJarMetadata(ModuleDescriptor descriptor) throws IOException {
        return getJarMetadata("test.jar", b -> b.withModuleInfo(descriptor));
    }

    private JarMetadata getJarMetadata(String path, ModFileCustomizer consumer) throws IOException {
        var testJar = tempDir.resolve(path);
        return getJarMetadata(testJar, consumer);
    }

    private JarMetadata getJarMetadata(Path testJar, ModFileCustomizer consumer) throws IOException {
        Files.createDirectories(testJar.getParent());

        var builder = ModFileBuilder.toJar(testJar);
        try {
            consumer.customize(builder);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        builder.build();

        try (var jc = JarContents.ofPath(testJar)) {
            var metadata = JarMetadata.from(jc);
            metadata.descriptor(); // This causes the packages to be scanned so we can close the underlying fs
            return metadata;
        }
    }

    private static ModuleDescriptor getJdkModuleDescriptor(Path testJar) {
        var modules = ModuleFinder.of(testJar).findAll();
        assertEquals(1, modules.size());
        return modules.iterator().next().descriptor();
    }

    @FunctionalInterface
    interface ModFileCustomizer {
        void customize(ModFileBuilder builder) throws IOException;
    }
}
