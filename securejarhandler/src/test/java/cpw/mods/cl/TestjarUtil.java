package cpw.mods.cl;

import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

class TestjarUtil {
    public record BuiltLayer(ModuleClassLoader cl, ModuleLayer layer, List<SecureJar> jars) implements AutoCloseable {
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
            for (SecureJar jar : jars) {
                jar.close();
            }
        }
    }

    public static BuiltLayer buildLayer(Path path, List<ModuleLayer> parentLayers) {
        return buildLayer(List.of(path), parentLayers);
    }

    public static BuiltLayer buildLayer(Path path, BuiltLayer parentLayer) {
        return buildLayer(List.of(path), List.of(parentLayer.layer()));
    }

    public static BuiltLayer buildLayer(Path path) {
        return buildLayer(path, List.of(ModuleLayer.boot()));
    }

    public static BuiltLayer buildLayer(List<Path> paths, List<ModuleLayer> parentLayers) {
        return buildLayer(paths, parentLayers, null);
    }

    public static BuiltLayer buildLayer(List<Path> paths, List<ModuleLayer> parentLayers, ClassLoader parentLoader) {
        var jars = paths.stream().map(p -> {
            try {
                return SecureJar.from(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toList();

        var roots = jars.stream().map(SecureJar::name).toList();
        var jf = JarModuleFinder.of(jars.toArray(SecureJar[]::new));
        var conf = Configuration.resolveAndBind(
                jf,
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                roots);

        Set<ModuleLayer> allParents = Collections.newSetFromMap(new IdentityHashMap<>());
        collectAllParents(parentLayers, allParents);

        var cl = new ModuleClassLoader(null, conf, allParents.stream().toList(), parentLoader);
        var layer = ModuleLayer.defineModules(conf, parentLayers, m -> cl).layer();
        return new BuiltLayer(cl, layer, jars);
    }

    public static ModuleLayer buildJdkLayer(List<Path> paths, List<ModuleLayer> parentLayers, ClassLoader parentLoader) {
        var jf = ModuleFinder.of(paths.toArray(Path[]::new));
        var conf = Configuration.resolveAndBind(
                jf,
                parentLayers.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                jf.findAll().stream().map(r -> r.descriptor().name()).toList());

        Set<ModuleLayer> allParents = Collections.newSetFromMap(new IdentityHashMap<>());
        collectAllParents(parentLayers, allParents);

        return ModuleLayer.defineModulesWithOneLoader(conf, parentLayers, parentLoader).layer();
    }

    private static void collectAllParents(List<ModuleLayer> parentLayers, Set<ModuleLayer> allParents) {
        for (var parent : parentLayers) {
            if (allParents.add(parent)) {
                collectAllParents(parent.parents(), allParents);
            }
        }
    }
}
