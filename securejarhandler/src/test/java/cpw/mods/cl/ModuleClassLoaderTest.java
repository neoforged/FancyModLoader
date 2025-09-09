package cpw.mods.cl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterator;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.spi.URLStreamHandlerProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import net.neoforged.fml.testlib.ModFileBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.TestAbortedException;

class ModuleClassLoaderTest {
    @TempDir
    static Path tempDir;

    static Path firstLayerJar;
    static Path secondLayerJar;
    static Path thirdLayerConsumerJar;
    static Path thirdLayerProviderAJar;
    static Path thirdLayerProviderBJar;

    @BeforeAll
    static void createTestJars() throws IOException {
        // Provides the interface and an implementation provided using META-INF/services/
        firstLayerJar = new ModFileBuilder(tempDir.resolve("layer1.jar"))
                .addClass("layer1.DummyService", """
                        public interface DummyService {
                        }
                        """)
                .addClass("layer1.DummyServiceImpl", """
                        public class DummyServiceImpl implements DummyService {
                        }
                        """)
                .addClass("DefaultPackageClass", "class DefaultPackageClass {}")
                .addTextFile("META-INF/dummy.txt", "from layer1")
                .withModuleInfo(ModuleDescriptor.newModule("layer1")
                        .exports("layer1")
                        .provides("layer1.DummyService", List.of("layer1.DummyServiceImpl"))
                        .packages(Set.of("layer1", "layer1resources"))
                        .build())
                // Since this is a named module, the "layer1resources" package should be claimed by this module
                .addTextFile("layer1resources/dummy.txt", "from layer1")
                .build();
        // Provides the interface and an implementation using META-INF/services/
        secondLayerJar = new ModFileBuilder(tempDir.resolve("layer2.jar"))
                .addCompileClasspath(firstLayerJar)
                .addTextFile("META-INF/dummy.txt", "from layer2")
                .addClass("layer2.DummyServiceImpl", """
                        public class DummyServiceImpl implements layer1.DummyService {
                        }
                        """)
                .addService("layer1.DummyService", "layer2.DummyServiceImpl")
                .build();
        // META-INF/services based provider jar on the same layer as the consumer
        thirdLayerProviderAJar = new ModFileBuilder(tempDir.resolve("layer3_provider_a.jar"))
                .addCompileClasspath(firstLayerJar)
                .addClass("layer3a.DummyServiceImpl", """
                        public class DummyServiceImpl implements layer1.DummyService {
                        }
                        """)
                .withManifest(Map.of(
                        // For testing the package information
                        "Implementation-Title", "t",
                        "Implementation-Version", "t",
                        "Implementation-Vendor", "t",
                        "Specification-Title", "t",
                        "Specification-Version", "t",
                        "Specification-Vendor", "t"))
                .addService("layer1.DummyService", "layer3a.DummyServiceImpl")
                .addTextFile("META-INF/dummy.txt", "from layer3.provider.a")
                // This is a packaged file that should show up in the module packages, but since this is
                // an automatic module, it doesn't and is visible generally.
                .addTextFile("layer3resources/dummy.txt", "from layer3")
                .build();
        // Module based provider jar on the same layer as the consumer
        // Consumer jar
        thirdLayerProviderBJar = new ModFileBuilder(tempDir.resolve("layer3_provider_b.jar"))
                .addModulePath(firstLayerJar)
                .addClass("layer3b.DummyServiceImpl", """
                        public class DummyServiceImpl implements layer1.DummyService {
                        }
                        """)
                // This class serves as an access proxy to access a resource from a package that is *not* unconditionally opened
                .addClass("layer3b.ResourceProxy", """
                        public class ResourceProxy {
                            public java.net.URL getResource() {
                                return this.getClass().getResource("dummy.txt");
                            }
                        }
                        """)
                .withModuleInfo(ModuleDescriptor.newModule("layer3.provider.b")
                        .requires("layer1")
                        .exports("layer3b")
                        .opens("layer3bresourcesopen") // This package is opened, so resources are visible to getResources
                        .provides("layer1.DummyService", List.of("layer3b.DummyServiceImpl"))
                        .packages(Set.of("layer3b", "layer3bresources"))
                        .build())
                .addTextFile("META-INF/dummy.txt", "from layer3.provider.b")
                // This is a packaged file that should show up in the module packages, but is ultimately not visible
                // from outside layer3b itself.
                .addTextFile("layer3bresources/dummy.txt", "from layer3b")
                .addTextFile("layer3bresourcesopen/dummy.txt", "from layer3b")
                .build();
        thirdLayerConsumerJar = new ModFileBuilder(tempDir.resolve("layer3.jar"))
                .addCompileClasspath(firstLayerJar)
                .withManifest(Map.of("Automatic-Module-Name", "layer3"))
                .addClass("layer3.ServiceLoaderProxy", """
                        import java.util.List;
                        import java.util.ServiceLoader;

                        public class ServiceLoaderProxy {
                            public static List<layer1.DummyService> load() {
                                return ServiceLoader.load(layer1.DummyService.class).stream()
                                        .map(ServiceLoader.Provider::get).toList();
                            }
                            public static List<layer1.DummyService> loadFromLayer() {
                                return ServiceLoader.load(ServiceLoaderProxy.class.getModule().getLayer(), layer1.DummyService.class).stream()
                                        .map(ServiceLoader.Provider::get).toList();
                            }
                        }
                        """)
                .addClass("layer3.PackagesProxy", """
                        public class PackagesProxy {
                            public static Package[] load() {
                                return Package.getPackages();
                            }
                        }
                        """)
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
                .addTextFile("META-INF/dummy.txt", "from layer3")
                // This is a packaged file that should show up in the module packages
                .addTextFile("layer3resources/dummy.txt", "from layer3")
                .build();
    }

    enum ConformanceScenario {
        JDK("JDK"),
        JDK_WITH_CLASSPATH("JDK (with jars on classpath)"),
        SJH("SJH"),
        SJH_WITH_CLASSPATH("SJH (with jars on classpath)");

        final String displayName;

        ConformanceScenario(String displayName) {
            this.displayName = displayName;
        }

        public boolean hasParentClassloader() {
            return this == JDK_WITH_CLASSPATH || this == SJH_WITH_CLASSPATH;
        }

        public boolean isJdkLoader() {
            return this == JDK || this == JDK_WITH_CLASSPATH;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Tests against a JDK modular loader to find conformance issues.
     */
    @Nested
    @ParameterizedClass
    @EnumSource(ConformanceScenario.class)
    class Conformance {
        ConformanceScenario scenario;
        ModuleLayer layer1;
        ModuleLayer layer2;
        ModuleLayer layer3;
        ClassLoader loader;
        URLClassLoader systemClassloader;
        List<AutoCloseable> closeables = new ArrayList<>();

        public Conformance(ConformanceScenario scenario) {
            this.scenario = scenario;
        }

        @FunctionalInterface
        interface LayerFactory {
            ModuleLayer create(List<Path> jars, List<ModuleLayer> parents, ClassLoader parentCl);
        }

        @BeforeEach
        void setUp() throws Exception {
            var rootLayer = ModuleLayer.boot();

            systemClassloader = null;
            if (scenario.hasParentClassloader()) {
                // Emulate that the jars for all layers were on the system classpath and that is used as the parent loader
                systemClassloader = new URLClassLoader(new URL[] {
                        firstLayerJar.toUri().toURL(),
                        secondLayerJar.toUri().toURL(),
                        thirdLayerConsumerJar.toUri().toURL(),
                        thirdLayerProviderAJar.toUri().toURL(),
                        thirdLayerProviderBJar.toUri().toURL()
                }, ClassLoader.getPlatformClassLoader());
            }

            LayerFactory layerFactory;
            if (scenario.isJdkLoader()) {
                layerFactory = TestjarUtil::buildJdkLayer;
            } else {
                layerFactory = (jars, parents, parentCl) -> {
                    var builtLayer = TestjarUtil.buildLayer(jars, parents, parentCl);
                    closeables.add(builtLayer);
                    return builtLayer.layer();
                };
            }

            layer1 = layerFactory.create(
                    List.of(firstLayerJar),
                    List.of(rootLayer),
                    systemClassloader);
            layer2 = layerFactory.create(
                    List.of(secondLayerJar),
                    List.of(layer1),
                    scenario.hasParentClassloader() ? layer1.findLoader("layer1") : null);
            layer3 = layerFactory.create(
                    List.of(thirdLayerConsumerJar, thirdLayerProviderAJar, thirdLayerProviderBJar),
                    List.of(layer2),
                    scenario.hasParentClassloader() ? layer2.findLoader("layer2") : null);
            loader = layer3.findLoader("layer3");
        }

        @AfterEach
        void tearDown() throws Exception {
            if (systemClassloader != null) {
                systemClassloader.close();
            }
            if (loader instanceof ModuleClassLoader moduleClassLoader) {
                moduleClassLoader.close();
            }
            for (var closeable : closeables) {
                closeable.close();
            }
            closeLayer(layer1);
            closeLayer(layer2);
            closeLayer(layer3);

            clearJarUrlHandlerCache();
        }

        private void closeLayer(ModuleLayer layer) throws Exception {
            if (layer == null) {
                return;
            }
            for (Module module : layer.modules()) {
                var loader = module.getClassLoader();
                if (loader instanceof ModuleClassLoader) {
                    return; // For ModularClassLoader we close all opened Jars directly.
                }
                var readers = getMethodLookup().findVarHandle(loader.getClass(), "moduleToReader", Map.class).get(loader);
                for (Object value : ((Map<?, ?>) readers).values()) {
                    ((ModuleReader) value).close();
                }
            }
        }

        @Nested
        class Resources {
            @Test
            void testGetMetaInfResourcesFirstResultMatchesGetResource() throws Exception {
                assertThatIterator(loader.getResources("META-INF/dummy.txt").asIterator())
                        .toIterable()
                        .first()
                        .isEqualTo(loader.getResource("META-INF/dummy.txt"));
            }

            @Test
            void testGetMetaInfResources() throws Exception {
                List<URL> expected = new ArrayList<>();
                // Looking at the internal implementation of the JDK loader, it uses a HashMap#values iteration
                // to build the list of URLs. This means the order is actually undefined.
                // The only thing that can be assumed is that the first element is the same that getResource would return,
                // which the loader guarantees by iteration over the same HashMap values, and just returning the first result.
                Collections.addAll(expected,
                        URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                        URI.create("jar:" + thirdLayerProviderAJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                        URI.create("jar:" + thirdLayerConsumerJar.toUri() + "!/META-INF/dummy.txt").toURL());

                // When a parent class-loader is visible, the resources can also be found
                if (scenario.hasParentClassloader()) {
                    Collections.addAll(expected,
                            // Interestingly, it will *also* return the second/third layer Jars from the parent module layer loaders
                            URI.create("jar:" + secondLayerJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                            URI.create("jar:" + firstLayerJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                            // And then the ones from the root URLClassLoader actually do *not* reverse in order
                            URI.create("jar:" + firstLayerJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                            URI.create("jar:" + secondLayerJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                            URI.create("jar:" + thirdLayerConsumerJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                            URI.create("jar:" + thirdLayerProviderAJar.toUri() + "!/META-INF/dummy.txt").toURL(),
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/META-INF/dummy.txt").toURL());
                }

                List<URL> urls = new ArrayList<>();
                loader.getResources("META-INF/dummy.txt").asIterator().forEachRemaining(urls::add);
                assertThat(urls).containsExactlyInAnyOrderElementsOf(expected);
            }

            // Getting resources from packages of modules should actually delegate down directly to that one module that claimed the package
            // This means the resource should not show up even if the jar is on the parent classloader
            // HOWEVER: When scanning packages of an automatic module, resource packages are not actually claimed, so resources from
            // multiple jars containing the resource will still show up, and if the jars are also in the parent loader, even twice.
            @Test
            void testGetResourcesFromResourceOnlyPackageOfAutomaticModule() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("layer3resources/dummy.txt").asIterator().forEachRemaining(urls::add);

                List<URL> expected = new ArrayList<>();
                Collections.addAll(expected,
                        URI.create("jar:" + thirdLayerProviderAJar.toUri() + "!/layer3resources/dummy.txt").toURL(),
                        URI.create("jar:" + thirdLayerConsumerJar.toUri() + "!/layer3resources/dummy.txt").toURL());

                // For unclaimed packages, the files will show up twice from the parent loader as well
                if (scenario.hasParentClassloader()) {
                    Collections.addAll(expected,
                            URI.create("jar:" + thirdLayerProviderAJar.toUri() + "!/layer3resources/dummy.txt").toURL(),
                            URI.create("jar:" + thirdLayerConsumerJar.toUri() + "!/layer3resources/dummy.txt").toURL());
                }

                assertThat(urls).containsExactlyInAnyOrderElementsOf(expected);
            }

            // Getting resources from packages of modules should actually delegate down directly to that one module that claimed the package.
            // However, the JDK loader only does so for the *local* packages in that loader, it does not reach for the parent layers,
            // unless those are visible via the parent classloader relationship.
            @Test
            void testGetResourcesFromResourceOnlyPackageOfNamedModuleInParentLayer() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("layer1resources/dummy.txt").asIterator().forEachRemaining(urls::add);

                if (scenario.hasParentClassloader()) {
                    assertThat(urls).containsOnly(URI.create("jar:" + firstLayerJar.toUri() + "!/layer1resources/dummy.txt").toURL());
                } else {
                    assertThat(urls).isEmpty();
                }
            }

            /**
             * The package "layer3bresources" is claimed by a named module, but is not unconditionally opened.
             * It should not be returned by getResources.
             */
            @Test
            void testGetResourcesFromEncapsulatedResourceOnlyPackageOfNamedModule() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("layer3bresources/dummy.txt").asIterator().forEachRemaining(urls::add);

                if (scenario == ConformanceScenario.SJH || scenario == ConformanceScenario.SJH_WITH_CLASSPATH) {
                    throw new TestAbortedException("KNOWN DIFFERENCE: ModuleClassLoader currently ignores encapsulation for resources, so it would return the resource here.");
                }

                // If the same jar is also visible via the parent classloader, it will show up once from there
                if (scenario.hasParentClassloader()) {
                    assertThat(urls).containsExactly(URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3bresources/dummy.txt").toURL());
                } else {
                    assertThat(urls).isEmpty();
                }
            }

            @Test
            void testGetResourceFromEncapsulatedResourceOnlyPackageOfNamedModule() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("layer3bresourcesopen/dummy.txt").asIterator().forEachRemaining(urls::add);

                // If the same jar is also visible via the parent classloader, it will show up once from there
                if (scenario.hasParentClassloader()) {
                    assertThat(urls).containsExactly(
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3bresourcesopen/dummy.txt").toURL(),
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3bresourcesopen/dummy.txt").toURL());
                } else {
                    assertThat(urls).containsExactly(
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3bresourcesopen/dummy.txt").toURL());
                }
            }

            @Test
            void testGetResourcesForClassFile() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("layer3b/DummyServiceImpl.class").asIterator().forEachRemaining(urls::add);

                // If the same jar is also visible via the parent classloader, it will show up once from there
                if (scenario.hasParentClassloader()) {
                    assertThat(urls).containsExactly(
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3b/DummyServiceImpl.class").toURL(),
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3b/DummyServiceImpl.class").toURL());
                } else {
                    assertThat(urls).containsExactly(
                            URI.create("jar:" + thirdLayerProviderBJar.toUri() + "!/layer3b/DummyServiceImpl.class").toURL());
                }
            }

            @Test
            void testGetResourcesForClassFileInParentLayer() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("layer2/DummyServiceImpl.class").asIterator().forEachRemaining(urls::add);

                if (scenario.hasParentClassloader()) {
                    // But with a parent classloader relationship, we see the class file twice.
                    // Once from being on the root URLClassLoader, but also from its own actual layer.
                    assertThat(urls).containsExactly(
                            URI.create("jar:" + secondLayerJar.toUri() + "!/layer2/DummyServiceImpl.class").toURL(),
                            URI.create("jar:" + secondLayerJar.toUri() + "!/layer2/DummyServiceImpl.class").toURL());
                } else {
                    // Without parent classloader relationship, parent layers aren't searched
                    assertThat(urls).isEmpty();
                }
            }

            @Test
            void testGetResourcesForEmptyString() throws Exception {
                List<URL> urls = new ArrayList<>();
                loader.getResources("").asIterator().forEachRemaining(urls::add);
                assertThat(urls).isEmpty();
            }

            @Test
            void testGetResourceForNonExistentResource() {
                assertNull(loader.getResource("doesntexist"));
            }

            @Test
            void testGetResourceForResourceInParentLoader() throws Exception {
                var url = loader.getResource("layer1resources/dummy.txt");
                if (scenario.hasParentClassloader()) {
                    assertNotNull(url);
                    try (var in = url.openStream()) {
                        assertThat(in).hasContent("from layer1");
                    }
                } else {
                    assertNull(url);
                }
            }

            /*
                Note on this test: findResource(moduleName, resourceName) is protected,
                but called directly by Module#getResourceAsStream. As such, if that works
                correctly, we're good.
                To this end, all modules have a file at META-INF/dummy.txt, which would overlay
                each other on the classpath, but the modular version should return the content
                from that module.
             */
            @ParameterizedTest
            @ValueSource(strings = { "layer1", "layer2", "layer3", "layer3.provider.a", "layer3.provider.b" })
            public void testModularGetResourceAsStream(String moduleName) throws Exception {
                var module = layer3.findModule(moduleName).get();
                try (var in = module.getResourceAsStream("META-INF/dummy.txt")) {
                    assertThat(in).hasContent("from " + moduleName);
                }
            }
        }

        @Nested
        class ServiceLoading {

            // ServiceLoader records which class called it. This tests it from an unnamed module outside the hierarchy (this JUnit test).
            @Test
            void testServiceLoaderFromClassLoaderWithinUnnamedModule() {
                var serviceClass = Objects.requireNonNull(Class.forName(layer1.findModule("layer1").orElseThrow(), "layer1.DummyService"));

                var providers = ServiceLoader.load(layer3, serviceClass).stream().map(ServiceLoader.Provider::type).toList();
                assertThat(providers).extracting(p -> p.getName() + " from module " + p.getModule().getName()).containsOnlyOnce(
                        "layer3b.DummyServiceImpl from module layer3.provider.b",
                        "layer3a.DummyServiceImpl from module layer3.provider.a",
                        "layer2.DummyServiceImpl from module layer2",
                        "layer1.DummyServiceImpl from module layer1");
            }

            // ServiceLoader records which class called it. This tests it from within layer3.
            @Test
            void testServiceLoaderFromClassLoaderWithinLayer3() throws Exception {
                var testClass = layer3.findLoader("layer3").loadClass("layer3.ServiceLoaderProxy");
                var loadMethod = testClass.getMethod("loadFromLayer");
                var providers = (List<?>) loadMethod.invoke(null);

                assertThat(providers)
                        .extracting(p -> {
                            var providerClass = p.getClass();
                            return providerClass.getName() + " from module " + providerClass.getModule().getName();
                        })
                        .containsOnlyOnce(
                                "layer3b.DummyServiceImpl from module layer3.provider.b",
                                "layer3a.DummyServiceImpl from module layer3.provider.a",
                                "layer2.DummyServiceImpl from module layer2",
                                "layer1.DummyServiceImpl from module layer1");
            }

            // ServiceLoader records which class called it. This tests it from an unnamed module outside the hierarchy (this JUnit test).
            @Test
            void testServiceLoaderFromModuleLayerWithinUnnamedModule() {
                var serviceClass = Objects.requireNonNull(Class.forName(layer1.findModule("layer1").orElseThrow(), "layer1.DummyService"));

                var providers = ServiceLoader.load(layer3, serviceClass).stream().map(ServiceLoader.Provider::type).toList();
                assertThat(providers).extracting(p -> p.getName() + " from module " + p.getModule().getName()).containsOnlyOnce(
                        "layer3b.DummyServiceImpl from module layer3.provider.b",
                        "layer3a.DummyServiceImpl from module layer3.provider.a",
                        "layer2.DummyServiceImpl from module layer2",
                        "layer1.DummyServiceImpl from module layer1");
            }

            // ServiceLoader records which class called it. This tests it from within layer3.
            @Test
            void testServiceLoaderFromModuleLayerWithinLayer3() throws Exception {
                var testClass = layer3.findLoader("layer3").loadClass("layer3.ServiceLoaderProxy");
                var loadMethod = testClass.getMethod("loadFromLayer");
                var providers = (List<?>) loadMethod.invoke(null);
                assertThat(providers)
                        .extracting(p -> {
                            var providerClass = p.getClass();
                            return providerClass.getName() + " from module " + providerClass.getModule().getName();
                        })
                        .containsOnlyOnce(
                                "layer3b.DummyServiceImpl from module layer3.provider.b",
                                "layer3a.DummyServiceImpl from module layer3.provider.a",
                                "layer2.DummyServiceImpl from module layer2",
                                "layer1.DummyServiceImpl from module layer1");
            }
        }

        @Nested
        class ClassLoading {
            // Tests that findClass with a module parameter only finds local classes
            @Test
            void testModularFindClass() throws Throwable {
                var layer3Class = callFindClass(loader, "layer3", "layer3.ServiceLoaderProxy");
                assertSame(loader, layer3Class.getClassLoader());

                assertNull(callFindClass(loader, "layer2", "layer2.DummyServiceImpl"));
            }

            // Tests that findClass without a module parameter only finds local classes
            @Test
            void testFindClass() throws Throwable {
                var layer3ClassJdk = callFindClass(loader, "layer3.ServiceLoaderProxy");
                assertSame(loader, layer3ClassJdk.getClassLoader());

                assertThatThrownBy(() -> callFindClass(loader, "layer2.DummyServiceImpl"))
                        .isExactlyInstanceOf(ClassNotFoundException.class)
                        .hasMessage("layer2.DummyServiceImpl");
            }

            // Shouldn't find anything but also shouldn't crash
            @Test
            void testFindClassDefaultPackage() {
                assertThatThrownBy(() -> callFindClass(loader, "DefaultPackageClass"))
                        .isExactlyInstanceOf(ClassNotFoundException.class)
                        .hasMessage("DefaultPackageClass");
            }

            @Test
            void testBehaviorIfClassWasAlreadyLoadedFromClasspath() throws Exception {
                if (systemClassloader == null) {
                    throw new TestAbortedException("Test only applies if there's a parent loader");
                }

                var classFromRoot = systemClassloader.loadClass("layer1.DummyService");
                var classFromLayer = loader.loadClass("layer1.DummyService");
                assertEquals(systemClassloader, classFromRoot.getClassLoader());
                assertEquals(layer1.findLoader("layer1"), classFromLayer.getClassLoader());
            }

            @Test
            void testLoadClassFromDefaultPackage() throws Exception {
                // If no parent loader is visible up to the classpath, a class from the default package can't be loaded
                if (!scenario.hasParentClassloader()) {
                    assertThatThrownBy(() -> loader.loadClass("DefaultPackageClass"))
                            .isExactlyInstanceOf(ClassNotFoundException.class)
                            .hasMessage("DefaultPackageClass");
                } else {
                    var loadedClass = loader.loadClass("DefaultPackageClass");
                    // Will be in the unnamed module, obviously
                    assertFalse(loadedClass.getModule().isNamed());
                }
            }

            @ParameterizedTest
            @ValueSource(strings = {
                    // Default package goes to parent classloader
                    "DefaultPackageClassDoesNotExist",
                    // Package not claimed by any module goes to parent classloader too
                    "unclaimed.DoesNotExist",
                    // Local module
                    "layer3.DoesNotExist",
                    // Parent layer module
                    "layer1.DoesNotExist"
            })
            void testLoadClassForNonExistentClass(String className) {
                assertThatThrownBy(() -> loader.loadClass(className))
                        .isExactlyInstanceOf(ClassNotFoundException.class)
                        .hasMessage(className);
            }

            @Test
            void testFindClassForBrokenJarFileThrowsClassNotFoundException() throws IOException {
                // Copy the first layer jar and open another layer to test this behavior
                var corruptedJar = tempDir.resolve("corruptedJar.jar");
                Files.copy(firstLayerJar, corruptedJar, StandardCopyOption.REPLACE_EXISTING);
                try (var layer = TestjarUtil.buildLayer(corruptedJar)) {
                    // This test is slightly different in that it corrupts the existing jar file
                    try (var file = new RandomAccessFile(corruptedJar.toFile(), "rwd")) {
                        for (long i = 0; i < file.length(); i++) {
                            file.writeByte(0);
                        }
                    }

                    // What we care about here is that the original IOException is the cause of the ClassNotFoundException,
                    // and it is not swallowed.
                    assertThatThrownBy(() -> layer.cl().findClass("layer1.DummyService"))
                            .isExactlyInstanceOf(ClassNotFoundException.class)
                            .hasMessage("layer1.DummyService")
                            .hasCauseInstanceOf(IOException.class);
                }
            }

            @Test
            void testGetDefinedPackages() throws Exception {
                loader.loadClass("layer3.ServiceLoaderProxy");
                assertThat(loader.getDefinedPackages()).extracting(Package::getName).containsOnly("layer3");
                loader.loadClass("layer3a.DummyServiceImpl");
                assertThat(loader.getDefinedPackages()).extracting(Package::getName).containsOnly("layer3", "layer3a");
                loader.loadClass("layer3b.DummyServiceImpl");
                assertThat(loader.getDefinedPackages()).extracting(Package::getName).containsOnly("layer3", "layer3a", "layer3b");
            }

            @Test
            void testGetDefinedPackagesDoesNotContainParentPackages() throws Exception {
                loader.loadClass("layer2.DummyServiceImpl");
                assertThat(loader.getDefinedPackages()).isEmpty();
            }

            @Test
            void testGetPackages() throws Exception {
                loader.loadClass("layer2.DummyServiceImpl");
                var packages = (Package[]) loader.loadClass("layer3.PackagesProxy").getMethod("load").invoke(null);
                // The spec says "ancestors" w.r.t. parent packages being present and that doesn't seem to be the case
                // if there's no actual parent loader, just a parent module layer.
                if (scenario.hasParentClassloader()) {
                    assertThat(packages).extracting(Package::getName).contains("layer3", "layer2");
                } else {
                    assertThat(packages).extracting(Package::getName).contains("layer3");
                }
            }

            @Test
            void testGetPackageForModule() throws Exception {
                // See class-doc for Package for details
                // Note that "unnamed module" doesn't mean automatic module, it really means the unnamed module.
                var pkg = loader.loadClass("layer3a.DummyServiceImpl").getPackage();
                assertEquals("layer3a", pkg.getName());
                assertNull(pkg.getImplementationTitle());
                assertNull(pkg.getImplementationVendor());
                assertNull(pkg.getImplementationVersion());
                assertNull(pkg.getSpecificationTitle());
                assertNull(pkg.getSpecificationVendor());
                assertNull(pkg.getSpecificationVersion());
                assertTrue(pkg.isSealed(thirdLayerProviderAJar.toUri().toURL()));
            }

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

            private Class<?> callFindClass(ClassLoader loader, String moduleName, String className) throws Throwable {
                var methodLookup = getMethodLookup();
                var findClass = methodLookup.findVirtual(loader.getClass(), "findClass", MethodType.methodType(Class.class, String.class, String.class));
                return (Class<?>) findClass.invoke(loader, moduleName, className);
            }

            private Class<?> callFindClass(ClassLoader loader, String className) throws Throwable {
                var methodLookup = getMethodLookup();
                var findClass = methodLookup.findVirtual(loader.getClass(), "findClass", MethodType.methodType(Class.class, String.class));
                return (Class<?>) findClass.invoke(loader, className);
            }
        }

        private static MethodHandles.Lookup getMethodLookup() {
            try {
                var hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                hackfield.setAccessible(true);
                return (MethodHandles.Lookup) hackfield.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Tests that services from parent layers are discoverable using normal serviceloader usage.
         */
        @Test
        public void testLoadServiceFromBootLayer() throws Exception {
            try (var layer1 = TestjarUtil.buildLayer(firstLayerJar);
                    var layer2 = TestjarUtil.buildLayer(secondLayerJar, layer1);
                    var layer3 = TestjarUtil.buildLayer(thirdLayerConsumerJar, layer2);
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
                // that is provided in the testjar_cp source set, which is on the classpath
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

    private static void clearJarUrlHandlerCache() throws Exception {
        // Annoyingly, any use of a Jar file via jar: URIs tends to create stale cache entries
        // in sun.net.www.protocol.jar.JarFileFactory.fileCache
        // However, we can get such a cached connection and close it explicitly, which then cleans it up from the cache
        for (var jarPath : List.of(firstLayerJar, secondLayerJar, thirdLayerProviderAJar, thirdLayerProviderBJar, thirdLayerConsumerJar)) {
            URL url = new URL("jar:" + jarPath.toUri() + "!/");
            ((JarURLConnection) url.openConnection()).getJarFile().close();
        }
    }
}
