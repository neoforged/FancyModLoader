package cpw.mods.cl.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.cl.ModuleClassLoader;
import java.net.spi.URLStreamHandlerProvider;
import java.nio.file.spi.FileSystemProvider;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class TestServiceLoader {
    /**
     * Tests that we can load services from modules that are part of the boot layer.
     * In principle this also tests that services correctly get loaded from parent module layers too.
     */
    @Test
    public void testLoadServiceFromBootLayer() throws Exception {
        TestjarUtil.withTestjar1Setup(cl -> {
            // We expect to find at least the unionfs provider
            ServiceLoader<FileSystemProvider> sl = TestjarUtil.loadTestjar1(cl, FileSystemProvider.class);
            boolean foundUnionFsProvider = sl.stream().map(ServiceLoader.Provider::get).anyMatch(p -> p.getScheme().equals("union"));

            assertTrue(foundUnionFsProvider, "Expected to be able to find the UFS provider");
        });
    }

    @Test
    public void testLoadServiceFromBootLayerNested() throws Exception {
        TestjarUtil.withTestjar2Setup(cl -> {
            // Try to find service from boot layer
            // We expect to find at least the unionfs provider
            ServiceLoader<FileSystemProvider> sl = TestjarUtil.loadTestjar2(cl, FileSystemProvider.class);
            boolean foundUnionFsProvider = sl.stream().map(ServiceLoader.Provider::get).anyMatch(p -> p.getScheme().equals("union"));

            assertTrue(foundUnionFsProvider, "Expected to be able to find the UFS provider");

            // Try to find service from testjar1 layer
            var foundService = TestjarUtil.loadTestjar2(cl, URLStreamHandlerProvider.class)
                    .stream()
                    .anyMatch(p -> p.type().getName().startsWith("cpw.mods.cl.testjar1"));

            assertTrue(foundService, "Expected to be able to find the provider in testjar1 layer");
        });
    }

    /**
     * Tests that services that would normally be loaded from the classpath
     * do not get loaded by {@link ModuleClassLoader}.
     * In other words, test that our class loader isolation also works with services.
     */
    @Test
    public void testClassPathServiceDoesNotLeak() throws Exception {
        // Test that the DummyURLStreamHandlerProvider service provider can be loaded from the classpath
        var foundService = TestjarUtil.loadClasspath(TestServiceLoader.class.getClassLoader(), URLStreamHandlerProvider.class)
                .stream()
                .anyMatch(p -> p.type().getName().startsWith("cpw.mods.testjar_cp"));

        assertTrue(foundService, "Could not find service in classpath using application class loader!");

        TestjarUtil.withTestjar1Setup(cl -> {
            // Test that the DummyURLStreamHandlerProvider service provider cannot be loaded
            // from the classpath via ModuleClassLoader
            var foundServiceMCL = TestjarUtil.loadTestjar1(cl, URLStreamHandlerProvider.class)
                    .stream()
                    .anyMatch(p -> p.type().getName().startsWith("cpw.mods.testjar_cp"));

            assertFalse(foundServiceMCL, "Could find service in classpath using application class loader!");
        });
    }
}
