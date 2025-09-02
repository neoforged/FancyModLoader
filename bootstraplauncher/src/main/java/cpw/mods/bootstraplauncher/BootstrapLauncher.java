/*
 * BootstrapLauncher - for launching Java programs with added modular fun!
 * Copyright (C) 2021 - cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.bootstraplauncher;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.VisibleForTesting;

public class BootstrapLauncher {
    private static final boolean DEBUG = System.getProperties().containsKey("bsl.debug");

    /**
     * This entrypoint is used by the FML junit integration to launch without classloader isolation.
     * It should not be used for any other purpose. For the consequences of reducing classloader isolation,
     * read the documentation on {@link ModuleClassLoader#ModuleClassLoader(String, Configuration, List, ClassLoader)}
     */
    @VisibleForTesting
    public static void unitTestingMain(String... args) throws Exception {
        System.err.println("*".repeat(80));
        System.err.println("Starting in unit testing mode. Misconfiguration may mask bugs that would occur in normal operation.");
        System.err.println("*".repeat(80));
        run(false, args);
    }

    public static void main(String... args) throws Exception {
        run(true, args);
    }

    @SuppressWarnings("unchecked")
    private static void run(boolean classloaderIsolation, String... args) throws Exception {
        var legacyClasspath = loadLegacyClassPath();
        // Ensure backwards compatibility if somebody reads this value later on.
        System.setProperty("legacyClassPath", String.join(File.pathSeparator, legacyClasspath));

        // Loaded modules (name -> fs location)
        var loadedModules = findLoadedModules();

        // TODO: find existing modules automatically instead of taking in an ignore list.
        // The ignore list exempts files that start with certain listed keywords from being turned into modules (like existing modules)
        var ignoreList = System.getProperty("ignoreList", "asm,securejarhandler");
        var ignores = ignoreList.split(",");

        // Tracks all previously encountered packages
        // This prevents subsequent modules from including packages from previous modules, which is disallowed by the module system
        var previousPackages = new HashSet<String>();
        // The list of all SecureJars, which represent one module
        var jars = new ArrayList<SecureJar>();
        // path to name lookup
        var pathLookup = new HashMap<Path, String>();
        // Map of filenames to their 'module number', where all filenames sharing the same 'module number' is combined into one
        var filenameMap = getMergeFilenameMap();
        // Map of 'module number' to the list of paths which are combined into that module
        var mergeMap = new LinkedHashMap<String, List<Path>>();

        var order = new ArrayList<String>();

        outer:
        for (var legacy : legacyClasspath) {
            var path = Paths.get(legacy);
            var filename = path.getFileName().toString();

            for (var filter : ignores) {
                if (filename.startsWith(filter)) {
                    if (DEBUG) {
                        System.out.println("bsl: file '" + legacy + "' ignored because filename starts with '" + filter + "'");
                    }
                    continue outer;
                }
            }

            if (DEBUG) {
                System.out.println("bsl: encountered path '" + legacy + "'");
            }

            if (Files.notExists(path)) continue;

            // This computes the module name for the given artifact
            String moduleName;
            try (var jarContent = JarContents.ofPath(path)) {
                moduleName = JarMetadata.from(jarContent).name();
            } catch (UncheckedIOException | IOException e) {
                if (DEBUG) {
                    System.out.println("bsl: skipping '" + path + "' due to an IO error: " + e);
                }
                continue;
            }

            if ("".equals(moduleName)) {
                continue;
            }

            // If a module of the same name is already loaded, skip it
            var existingModuleLocation = loadedModules.get(moduleName);
            if (existingModuleLocation != null) {
                if (!existingModuleLocation.equals(path)) {
                    throw new IllegalStateException("Module named " + moduleName + " was already on the JVMs module path loaded from " +
                            existingModuleLocation + " but class-path contains it at location " + path);
                }

                if (DEBUG) {
                    System.out.println("bsl: skipping '" + path + "' because it is already loaded on boot-path as " + moduleName);
                }
                continue;
            }

            var jarname = pathLookup.computeIfAbsent(path, k -> filenameMap.getOrDefault(filename, moduleName));
            order.add(jarname);
            mergeMap.computeIfAbsent(jarname, k -> new ArrayList<>()).add(path);
        }

        // Iterate over merged modules map and combine them into one SecureJar each
        mergeMap.entrySet().stream().sorted(Comparator.comparingInt(e -> order.indexOf(e.getKey()))).forEach(e -> {
            // skip empty paths
            var name = e.getKey();
            var paths = e.getValue();
            if (paths.size() == 1 && Files.notExists(paths.get(0))) return;
            var pathsArray = paths.toArray(Path[]::new);
            var tracker = new PackageTracker(Set.copyOf(previousPackages), pathsArray);
            JarContents jarContents;
            try {
                jarContents = JarContents.ofFilteredPaths(
                        paths.stream().map(path -> new JarContents.FilteredPath(path, tracker)).toList());
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to build merged jar from " + paths, ex);
            }
            var jar = SecureJar.from(jarContents);
            var packages = jar.moduleDataProvider().descriptor().packages();

            if (DEBUG) {
                System.out.println("bsl: the following paths are merged together in module " + name);
                paths.forEach(path -> System.out.println("bsl:    " + path));
                System.out.println("bsl: list of packages for module " + name);
                packages.forEach(p -> System.out.println("bsl:    " + p));
            }

            previousPackages.addAll(packages);
            jars.add(jar);
        });

        var secureJarsArray = jars.toArray(SecureJar[]::new);

        // Gather all the module names from the SecureJars
        var allTargets = Arrays.stream(secureJarsArray).map(SecureJar::name).toList();
        // Creates a module finder which uses the list of SecureJars to find modules from
        var jarModuleFinder = JarModuleFinder.of(secureJarsArray);
        // Retrieve the boot layer's configuration
        var bootModuleConfiguration = ModuleLayer.boot().configuration();

        // Creates the module layer configuration for the bootstrap layer module
        // The parent configuration is the boot layer configuration (above)
        // The `before` module finder, used to find modules "in" this layer, and is the jar module finder above
        // The `after` module finder, used to find modules that aren't in the jar module finder or the parent configuration,
        //   is the system module finder (which is probably in the boot configuration :hmmm:)
        // And the list of root modules for this configuration (that is, the modules that 'belong' to the configuration) are
        // the above modules from the SecureJars
        var bootstrapConfiguration = bootModuleConfiguration.resolveAndBind(jarModuleFinder, ModuleFinder.ofSystem(), allTargets);
        // If the classloading should be isolated, we do not configure a parent loader, otherwise we use the context CL
        ClassLoader parentLoader = classloaderIsolation ? null : Thread.currentThread().getContextClassLoader();
        // Creates the module class loader, which does the loading of classes and resources from the bootstrap module layer/configuration,
        // falling back to the boot layer if not in the bootstrap layer
        var moduleClassLoader = new ModuleClassLoader("MC-BOOTSTRAP", bootstrapConfiguration, List.of(ModuleLayer.boot()), parentLoader);
        // Actually create the module layer, using the bootstrap configuration above, the boot layer as the parent layer (as configured),
        // and mapping all modules to the module class loader
        var layer = ModuleLayer.defineModules(bootstrapConfiguration, List.of(ModuleLayer.boot()), m -> moduleClassLoader);
        // Set the context class loader to the module class loader from this point forward
        Thread.currentThread().setContextClassLoader(moduleClassLoader);

        // Invoke Launcher via reflection
        var launcherClass = Class.forName("cpw.mods.modlauncher.Launcher", true, moduleClassLoader);
        var launcherMain = launcherClass.getMethod("main", String[].class);
        launcherMain.invoke(null, (Object) args); // cast to disambiguate with vararg
    }

    /**
     * Find a mapping from module-name to filesystem location for the modules that are on the JVMs boot module path.
     */
    private static Map<String, Path> findLoadedModules() {
        record ModuleWithLocation(String name, Path location) {}
        return ModuleLayer.boot().configuration().modules().stream()
                .map(module -> {
                    var reference = module.reference();
                    var moduleName = reference.descriptor().name();
                    var locationUri = reference.location().orElse(null);
                    if (moduleName.isBlank() || locationUri == null) {
                        return null;
                    }

                    Path location;
                    try {
                        location = new File(locationUri).toPath();
                    } catch (IllegalArgumentException ignored) {
                        return null; // Ignore existing modules with non-filesystem locations
                    }

                    return new ModuleWithLocation(moduleName, location);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ModuleWithLocation::name, ModuleWithLocation::location));
    }

    private static Map<String, String> getMergeFilenameMap() {
        var mergeModules = System.getProperty("mergeModules");
        if (mergeModules == null)
            return Map.of();
        // `mergeModules` is a semicolon-separated set of comma-separated set of paths, where each (comma) set of paths is
        // combined into a single modules
        // example: filename1.jar,filename2.jar;filename2.jar,filename3.jar

        Map<String, String> filenameMap = new HashMap<>();
        int i = 0;
        for (var merge : mergeModules.split(";")) {
            var targets = merge.split(",");
            for (String target : targets) {
                filenameMap.put(target, String.valueOf(i));
            }
            i++;
        }

        return filenameMap;
    }

    private record PackageTracker(Set<String> packages, Path... paths) implements JarContents.PathFilter {
        @Override
        public boolean test(String relativePath) {
            // This method returns true if the given path is allowed within the JAR (filters out 'bad' paths)

            if (packages.isEmpty() || // This is the first jar, nothing is claimed yet, so allow everything
                    relativePath.startsWith("META-INF/")) // Every module can have their own META-INF
                return true;

            int idx = relativePath.lastIndexOf('/');
            return idx < 0 || // Resources at the root are allowed to co-exist
                    idx == relativePath.length() - 1 || // All directories can have a potential to exist without conflict, we only care about real files.
                    !packages.contains(relativePath.substring(0, idx).replace('/', '.')); // If the package hasn't been used by a previous JAR
        }
    }

    private static List<String> loadLegacyClassPath() {
        var legacyCpPath = System.getProperty("legacyClassPath.file");

        if (legacyCpPath != null) {
            var legacyCPFileCandidatePath = Paths.get(legacyCpPath);
            try {
                return Files.readAllLines(legacyCPFileCandidatePath);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load the legacy class path from the specified file: " + legacyCpPath, e);
            }
        }

        var legacyClasspath = System.getProperty("legacyClassPath", System.getProperty("java.class.path"));
        Objects.requireNonNull(legacyClasspath, "Missing legacyClassPath, cannot bootstrap");
        if (legacyClasspath.isEmpty()) {
            return List.of();
        } else {
            return Arrays.asList(legacyClasspath.split(File.pathSeparator));
        }
    }
}
