package cpw.mods.cl;

import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

class TestjarUtil {
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
}
