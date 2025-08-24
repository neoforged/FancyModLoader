package cpw.mods.cl.test;

import cpw.mods.cl.ModuleClassLoader;
import net.neoforged.fml.testlib.ModFileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.module.ModuleDescriptor;
import java.net.spi.URLStreamHandlerProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class TestServiceLoader {
    @TempDir
    Path tempDir;

    /**
     * Tests that services from parent layers are discoverable using normal serviceloader usage.
     */
    @Test
    public void testLoadServiceFromBootLayer() throws Exception {
        // Provides the interface and an implementation provided using META-INF/services/
        var firstLayerJar = new ModFileBuilder(tempDir.resolve("layer1.jar"))
                .addClass("layer1.DummyService", """
                        public interface DummyService {
                        }
                        """)
                .addClass("layer1.DummyServiceImpl", """
                        public class DummyServiceImpl implements DummyService {
                        }
                        """)
                .withModuleInfo(ModuleDescriptor.newModule("layer1")
                        .exports("layer1")
                        .provides("layer1.DummyService", List.of("layer1.DummyServiceImpl"))
                        .build())
                .build();
        // Provides the interface and an implementation using META-INF/services/
        var secondLayerJar = new ModFileBuilder(tempDir.resolve("layer2.jar"))
                .addCompileClasspath(firstLayerJar)
                .addClass("layer2.DummyServiceImpl", """
                        public class DummyServiceImpl implements layer1.DummyService {
                        }
                        """)
                .addService("layer1.DummyService", "layer2.DummyServiceImpl")
                .build();
        // Consumer jar
        var thirdLayerJar = new ModFileBuilder(tempDir.resolve("layer3.jar"))
                .addCompileClasspath(firstLayerJar)
                .addClass("layer3.ServiceLoaderProxy", """
                        import java.util.List;
                        import java.util.ServiceLoader;
                        
                        public class ServiceLoaderProxy {
                            public static List<layer1.DummyService> load() {
                                return ServiceLoader.load(layer1.DummyService.class).stream()
                                        .map(ServiceLoader.Provider::get).toList();
                            }
                        }
                        """)
                .build();

        try (var layer1 = TestjarUtil.buildLayer(firstLayerJar);
             var layer2 = TestjarUtil.buildLayer(secondLayerJar, layer1);
             var layer3 = TestjarUtil.buildLayer(thirdLayerJar, layer2);
             var ignored = layer3.makeLoaderCurrent()) {

            var testClass = layer3.cl().loadClass("layer3.ServiceLoaderProxy");
            var loadMethod = testClass.getMethod("load");
            var loadedServices = (List<?>) loadMethod.invoke(null);
            assertThat(loadedServices)
                    .extracting(o -> o.getClass().getName())
                    .containsOnly("layer1.DummyServiceImpl", "layer2.DummyServiceImpl");
        }
    }

    /**
     * Tests that services that would normally be loaded from the classpath
     * do not get loaded by {@link ModuleClassLoader}.
     * In other words, test that our class loader isolation also works with services.
     */
    @Test
    public void testClassPathServiceDoesNotLeak() throws Exception {
        // Check that our current context classloader can load the dummy implementation of URLStreamHandlerProvider
        // that is provided in the testjar_cp source set, which is on the classpath
        assertThat(ServiceLoader.load(URLStreamHandlerProvider.class).stream().toList())
                .extracting(p -> p.type().getName())
                .contains("cpw.mods.testjar_cp.DummyURLStreamHandlerProvider");

        // Now build a consumer jar in an isolated layer and try to load the same service
        // It should NOT be able to load the DummyURLStreamHandlerProvider as that is only
        // on the classpath, and ModuleClassLoader should not delegate to the classpath
        var consumerJar = new ModFileBuilder(tempDir.resolve("consumer.jar"))
                .addClass("consumer.ServiceLoaderProxy", """
                        import java.util.List;
                        import java.util.ServiceLoader;
                        import java.net.spi.URLStreamHandlerProvider;
                        
                        public class ServiceLoaderProxy {
                            public static List<URLStreamHandlerProvider> load() {
                                return ServiceLoader.load(URLStreamHandlerProvider.class).stream()
                                        .map(ServiceLoader.Provider::get).toList();
                            }
                        }
                        """)
                .build();

        try (var consumerLayer = TestjarUtil.buildLayer(consumerJar);
             var ignored = consumerLayer.makeLoaderCurrent()) {
            var testClass = consumerLayer.cl().loadClass("consumer.ServiceLoaderProxy");
            var loadMethod = testClass.getMethod("load");
            var loadedServices = (List<?>) loadMethod.invoke(null);

            assertThat(loadedServices)
                    .extracting(o -> o.getClass().getName())
                    .doesNotContain("cpw.mods.testjar_cp.DummyURLStreamHandlerProvider");
        }
    }
}
