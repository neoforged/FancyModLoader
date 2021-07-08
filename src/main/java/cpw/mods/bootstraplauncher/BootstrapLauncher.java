package cpw.mods.bootstraplauncher;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;

import java.lang.module.ModuleFinder;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BootstrapLauncher {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        var legacyCP = Objects.requireNonNull(System.getProperty("legacyClassPath"), "Missing legacyClassPath, cannot bootstrap");
        var versionName = Objects.requireNonNull(System.getProperty("versionName"), "Missing versionName, cannot bootstrap");
        var ignoreList = System.getProperty("ignoreList", "/org/ow2/asm/");
        var ignores = Arrays.stream(ignoreList.split("~~")).toList();
        var fileList = Arrays.stream(legacyCP.split("~~"))
                .filter(n->!n.endsWith(versionName+".jar"))
                .filter(n-> ignores.stream().noneMatch(n::contains))
                .map(Paths::get)
                .collect(Collectors.toList());
        var previousPkgs = new HashSet<String>();
        var finder = fileList.stream()
                .map(paths -> SecureJar.from(new PkgTracker(Set.copyOf(previousPkgs)), paths))
                .peek(sj->previousPkgs.addAll(sj.getPackages()))
                .toArray(SecureJar[]::new);
        var alltargets = Arrays.stream(finder).map(SecureJar::name).toList();
        var jf = JarModuleFinder.of(finder);
        var cf = ModuleLayer.boot().configuration();
        var newcf = cf.resolveAndBind(jf, ModuleFinder.ofSystem(), alltargets);
        var mycl = new ModuleClassLoader("MC-BOOTSTRAP", newcf, List.of(ModuleLayer.boot()));
        var layer = ModuleLayer.defineModules(newcf, List.of(ModuleLayer.boot()), m->mycl);
        Thread.currentThread().setContextClassLoader(mycl);

        final var loader = ServiceLoader.load(layer.layer(), Consumer.class);
        ((Consumer<String[]>)loader.stream().findFirst().orElseThrow().get()).accept(args);
    }

    private record PkgTracker(Set<String> packages) implements BiPredicate<String, String> {
        @Override
        public boolean test(final String path, final String basePath) {
            if (packages.isEmpty()) return true;
            if (path.startsWith("META-INF/")) return true;
            int idx = path.lastIndexOf('/');
            return idx < 0 || !packages.contains(path.substring(0, idx).replace('/', '.'));
        }
    }
}
