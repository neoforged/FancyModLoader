package cpw.mods.cl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.module.ModuleDescriptor;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.spi.URLStreamHandlerProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import net.neoforged.fml.testlib.ModFileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleClassLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    public void testPackageInfoAvailability() throws Exception {
        var testJar = new ModFileBuilder(tempDir.resolve("packagetest.jar"))
                .addClass("testpkg.package-info", """
                        @TestAnnotation
                        package testpkg;
                        """)
                .addClass("testpkg.TestAnnotation", """
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface TestAnnotation {
                        }
                        """)
                .build();

        try (var layer = TestjarUtil.buildLayer(testJar)) {
            // Reference package through a class to ensure correct behavior of ModuleClassLoader#findClass(String,String)
            Class<?> cls = Class.forName("testpkg.TestAnnotation", true, layer.cl());
            assertThat(cls.getPackage().getDeclaredAnnotations())
                    .extracting(Annotation::annotationType)
                    .extracting(Class::getName)
                    .containsOnly("testpkg.TestAnnotation");
        }
    }

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
        // Create a provider that would be accessible from the normal classpath
        var parentJar = tempDir.resolve("cpprovider");
        new ModFileBuilder(parentJar)
                .addClass("DummyURLStreamHandlerProvider", """
                        import java.net.URLStreamHandler;
                        import java.net.spi.URLStreamHandlerProvider;

                        public class DummyURLStreamHandlerProvider extends URLStreamHandlerProvider {
                            @Override
                            public URLStreamHandler createURLStreamHandler(String protocol) {
                                return null;
                            }
                        }
                        """)
                .addService(URLStreamHandlerProvider.class, "DummyURLStreamHandlerProvider")
                .build();

        var previousCl = Thread.currentThread().getContextClassLoader();
        try (var cpLoader = new URLClassLoader(new URL[] { parentJar.toUri().toURL() })) {
            Thread.currentThread().setContextClassLoader(cpLoader);

            // Check that our current context classloader can load the dummy implementation of URLStreamHandlerProvider
            assertThat(ServiceLoader.load(URLStreamHandlerProvider.class).stream().toList())
                    .extracting(p -> p.type().getName())
                    .contains("DummyURLStreamHandlerProvider");

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
                        .doesNotContain("DummyURLStreamHandlerProvider");
            }

        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }
}
