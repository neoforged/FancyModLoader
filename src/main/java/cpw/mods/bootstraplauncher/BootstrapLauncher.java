package cpw.mods.bootstraplauncher;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BootstrapLauncher {
    private static final boolean DEBUG = System.getProperties().containsKey("bsl.debug");
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        var legacyCP = loadLegacyClassPath();
        System.setProperty("legacyClassPath", String.join(File.pathSeparator, legacyCP)); //Ensure backwards compatibility if somebody reads this value later on.
        var ignoreList = System.getProperty("ignoreList", "/org/ow2/asm/,securejarhandler"); //TODO: find existing modules automatically instead of taking in an ignore list.
        var ignores = ignoreList.split(",");

        var previousPkgs = new HashSet<String>();
        var jars = new ArrayList<>();
        var filenameMap = getMergeFilenameMap();
        var mergeMap = new HashMap<Integer, List<Path>>();

        outer:
        for (var legacy : legacyCP) {
            for (var filter : ignores) {
                if (legacy.contains(filter)) {
                    if (DEBUG)
                        System.out.println(legacy + " IGNORED: " + filter);
                    continue outer;
                }
            }

            var path = Paths.get(legacy);
            if (DEBUG)
                System.out.println(path);
            var filename = path.getFileName().toString();
            if (filenameMap.containsKey(filename)) {
                mergeMap.computeIfAbsent(filenameMap.get(filename), k -> new ArrayList<>()).add(path);
                continue;
            }
            var jar = SecureJar.from(new PkgTracker(Set.copyOf(previousPkgs), path), path);
            var pkgs = jar.getPackages();
            if (DEBUG)
                pkgs.forEach(p -> System.out.println("  " + p));
            previousPkgs.addAll(pkgs);
            jars.add(jar);
        }
        mergeMap.forEach((idx, paths) -> {
            var pathsArray = paths.toArray(Path[]::new);
            var jar = SecureJar.from(new PkgTracker(Set.copyOf(previousPkgs), pathsArray), pathsArray);
            var pkgs = jar.getPackages();
            if (DEBUG) {
                paths.forEach(System.out::println);
                pkgs.forEach(p -> System.out.println("  " + p));
            }
            previousPkgs.addAll(pkgs);
            jars.add(jar);
        });
        var finder = jars.toArray(SecureJar[]::new);

        var alltargets = Arrays.stream(finder).map(SecureJar::name).toList();
        var jf = JarModuleFinder.of(finder);
        var cf = ModuleLayer.boot().configuration();
        var newcf = cf.resolveAndBind(jf, ModuleFinder.ofSystem(), alltargets);
        var mycl = new ModuleClassLoader("MC-BOOTSTRAP", newcf, List.of(ModuleLayer.boot()));
        var layer = ModuleLayer.defineModules(newcf, List.of(ModuleLayer.boot()), m->mycl);
        Thread.currentThread().setContextClassLoader(mycl);

        final var loader = ServiceLoader.load(layer.layer(), Consumer.class);
        // This *should* find the service exposed by ModLauncher's BootstrapLaunchConsumer {This doc is here to help find that class next time we go looking}
        ((Consumer<String[]>)loader.stream().findFirst().orElseThrow().get()).accept(args);
    }

    private static Map<String, Integer> getMergeFilenameMap() {
        // filename1.jar,filename2.jar;filename2.jar,filename3.jar
        var mergeModules = System.getProperty("mergeModules");
        if (mergeModules == null)
            return Map.of();

        Map<String, Integer> filenameMap = new HashMap<>();
        int i = 0;
        for (var merge : mergeModules.split(";")) {
            var targets = merge.split(",");
            for (String target : targets) {
                filenameMap.put(target, i);
            }
            i++;
        }

        return filenameMap;
    }

    private record PkgTracker(Set<String> packages, Path... paths) implements BiPredicate<String, String> {
        @Override
        public boolean test(final String path, final String basePath) {
            if (packages.isEmpty()         || // the first jar, nothing is claimed yet
                path.startsWith("META-INF/")) // Every module can have a meta-inf
                return true;

            int idx = path.lastIndexOf('/');
            return idx < 0 || // Something in the root of the module.
                idx == path.length() - 1 || // All directories can have a potential to exist without conflict, we only care about real files.
                !packages.contains(path.substring(0, idx).replace('/', '.'));
        }
    }

    private static List<String> loadLegacyClassPath() {
        var legacyCpPath = System.getProperty("legacyClassPath.file");

        if (legacyCpPath != null) {
            var legacyCPFileCandidatePath = Paths.get(legacyCpPath);
            if (Files.exists(legacyCPFileCandidatePath) && Files.isRegularFile(legacyCPFileCandidatePath)) {
                try {
                    return Files.readAllLines(legacyCPFileCandidatePath);
                }
                catch (IOException e) {
                    throw new IllegalStateException("Failed to load the legacy class path from the specified file: " + legacyCpPath, e);
                }
            }
        }

        return Arrays.asList(Objects.requireNonNull(System.getProperty("legacyClassPath", System.getProperty("java.class.path")), "Missing legacyClassPath, cannot bootstrap")
                               .split(File.pathSeparator));
    }
}
