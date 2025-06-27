package cpw.mods.cl.test;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public class TestjarUtil {
    private record BuiltLayer(ModuleClassLoader cl, ModuleLayer layer) {}

    /**
     * Build a layer for a {@code testjarX} source set.
     */
    private static BuiltLayer buildTestjarLayer(int testjar, List<ModuleLayer> parentLayers) {
        var paths = Stream.of(System.getenv("sjh.testjar" + testjar).split(File.pathSeparator))
                .map(Paths::get)
                .toArray(Path[]::new);
        var jar = SecureJar.from(paths);

        var roots = List.of(jar.name());
        var jf = JarModuleFinder.of(jar);
        var conf = Configuration.resolveAndBind(
                jf,
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                roots);
        var cl = new ModuleClassLoader("testjar2-layer", conf, parentLayers);
        var layer = ModuleLayer.defineModules(conf, parentLayers, m -> cl).layer();
        return new BuiltLayer(cl, layer);
    }

    private static void withClassLoader(ClassLoader cl, TestCallback callback) throws Exception {
        // Replace context classloader during the callback
        var previousCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(cl);

        try {
            callback.test(cl);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    /**
     * Load the {@code testjar1} source set as new module into a new layer,
     * and run the callback with the new layer's classloader.
     */
    public static void withTestjar1Setup(TestCallback callback) throws Exception {
        var built = buildTestjarLayer(1, List.of(ModuleLayer.boot()));

        withClassLoader(built.cl, callback);
    }

    /**
     * Load the {@code testjar2} source set as new module into a new layer,
     * whose parent is a layer loaded from the {@code testjar1} source set.
     */
    public static void withTestjar2Setup(TestCallback callback) throws Exception {
        var built1 = buildTestjarLayer(1, List.of(ModuleLayer.boot()));
        var built2 = buildTestjarLayer(2, List.of(built1.layer));

        withClassLoader(built2.cl, callback);
    }

    @FunctionalInterface
    public interface TestCallback {
        void test(ClassLoader cl) throws Exception;
    }

    /**
     * Instantiates a {@link ServiceLoader} within the testjar1 module.
     */
    public static <S> ServiceLoader<S> loadTestjar1(ClassLoader cl, Class<S> clazz) throws Exception {
        // Use the `load` method from the testjar sourceset.
        var testClass = cl.loadClass("cpw.mods.cl.testjar1.ServiceLoaderTest");
        var loadMethod = testClass.getMethod("load", Class.class);
        //noinspection unchecked
        return (ServiceLoader<S>) loadMethod.invoke(null, clazz);
    }

    /**
     * Instantiates a {@link ServiceLoader} within the testjar2 module.
     */
    public static <S> ServiceLoader<S> loadTestjar2(ClassLoader cl, Class<S> clazz) throws Exception {
        // Use the `load` method from the testjar sourceset.
        var testClass = cl.loadClass("cpw.mods.cl.testjar2.ServiceLoaderTest");
        var loadMethod = testClass.getMethod("load", Class.class);
        //noinspection unchecked
        return (ServiceLoader<S>) loadMethod.invoke(null, clazz);
    }

    /**
     * Instantiates a {@link ServiceLoader} within the classpath source set.
     */
    public static <S> ServiceLoader<S> loadClasspath(ClassLoader cl, Class<S> clazz) throws Exception {
        // Use the `load` method from the testjar sourceset.
        var testClass = cl.loadClass("cpw.mods.testjar_cp.ServiceLoaderTest");
        var loadMethod = testClass.getMethod("load", Class.class);
        //noinspection unchecked
        return (ServiceLoader<S>) loadMethod.invoke(null, clazz);
    }
}
