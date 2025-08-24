package cpw.mods.cl.test;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

public class TestjarUtil {
    public record BuiltLayer(ModuleClassLoader cl, ModuleLayer layer, SecureJar jar) implements AutoCloseable {
        public AutoCloseable makeLoaderCurrent() {
            // Replace context classloader during the callback
            var previousCl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(cl);
            return () -> {
                Thread.currentThread().setContextClassLoader(previousCl);
            };
        }

        @Override
        public void close() throws IOException {
            jar.close();
        }
    }

    /**
     * Build a layer for a {@code testjarX} source set.
     */
    private static BuiltLayer buildTestjarLayer(int testjar, List<ModuleLayer> parentLayers) throws IOException {
        var paths = Stream.of(System.getenv("sjh.testjar" + testjar).split(File.pathSeparator))
                .map(Paths::get)
                .toList();
        return buildLayer(paths, parentLayers);
    }

    public static BuiltLayer buildLayer(Path path, List<ModuleLayer> parentLayers) throws IOException {
        return buildLayer(List.of(path), parentLayers);
    }

    public static BuiltLayer buildLayer(Path path, BuiltLayer parentLayer) throws IOException {
        return buildLayer(List.of(path), List.of(parentLayer.layer()));
    }

    public static BuiltLayer buildLayer(Path path) throws IOException {
        return buildLayer(path, List.of(ModuleLayer.boot()));
    }

    public static BuiltLayer buildLayer(List<Path> paths, List<ModuleLayer> parentLayers) throws IOException {
        var jar = SecureJar.from(paths.toArray(Path[]::new));

        var roots = List.of(jar.name());
        var jf = JarModuleFinder.of(jar);
        var conf = Configuration.resolveAndBind(
                jf,
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                roots);

        Set<ModuleLayer> allParents = Collections.newSetFromMap(new IdentityHashMap<>());
        collectAllParents(parentLayers, allParents);

        var cl = new ModuleClassLoader(jar.name(), conf, allParents.stream().toList());
        var layer = ModuleLayer.defineModules(conf, parentLayers, m -> cl).layer();
        return new BuiltLayer(cl, layer, jar);
    }

    private static void collectAllParents(List<ModuleLayer> parentLayers, Set<ModuleLayer> allParents) {
        for (var parent : parentLayers) {
            if (allParents.add(parent)) {
                collectAllParents(parent.parents(), allParents);
            }
        }
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
