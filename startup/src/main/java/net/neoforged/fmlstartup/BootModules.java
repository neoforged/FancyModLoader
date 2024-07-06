/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class BootModules {
    /**
     * The set of modules we add ourselves, that are needed by FML to work.
     */
    private static final List<String> APP_MODULES = List.of(
            "maven.artifact",
            "net.neoforged.accesstransformer",
            "net.neoforged.accesstransformer.parser",
            "net.neoforged.accesstransformer.modlauncher",
            "net.neoforged.bus",
            // Transitive dep of bus:
            "net.jodah.typetools",
            "com.electronwill.nightconfig.core",
            "com.electronwill.nightconfig.toml",
            "net.neoforged.mergetool.api",
            "org.objectweb.asm",
            "org.objectweb.asm.tree",
            "cpw.mods.modlauncher",
            "fml_earlydisplay",
            "fml_loader",
            "JarJarSelector",
            "JarJarMetadata",
            "org.spongepowered.mixin"
    );

    /**
     * This is the set of platform modules that we expect Minecraft to add,
     * as well as modules we expect to be on the boot module path such that
     * they are loaded in the app classloader.
     */
    private static final List<String> BOOT_MODULES = List.of(
            "JarJarFileSystems",
            "cpw.mods.securejarhandler",
            "org.objectweb.asm",
            "org.objectweb.asm.tree",
            // Math library
            "org.joml",
            // OpenGL Access and various Natives
            "org.lwjgl",
            "org.lwjgl.*",
            // Logging, Commons
            "org.apache.*",
            "org.slf4j",
            "org.slf4j.*",
            // This is Mojangs logging library
            "logging",
            // Argument parsing
            "jopt.simple",
            // GSON
            "com.google.gson",
            // Guava
            "com.google.common");

    private static final Set<String> DIRECT_MATCHES = new HashSet<>();
    private static final List<String> PREFIX_MATCHES = new ArrayList<>();

    static {
        for (String bootModule : BOOT_MODULES) {
            if (bootModule.endsWith("*")) {
                PREFIX_MATCHES.add(bootModule.substring(0, bootModule.length() - 1));
            } else {
                DIRECT_MATCHES.add(bootModule);
            }
        }

        DIRECT_MATCHES.addAll(APP_MODULES);
    }

    public static List<String> getMissingRequiredModules(List<String> startupModules) {
        var missingModules = new ArrayList<String>();
        for (String requiredBootModule : APP_MODULES) {
            if (!startupModules.contains(requiredBootModule)) {
                missingModules.add(requiredBootModule);
            }
        }
        return missingModules;
    }

    public static boolean isBootModule(@Nullable String moduleName) {
        if (moduleName == null) {
            return false;
        }
        if (DIRECT_MATCHES.contains(moduleName)) {
            return true;
        }
        for (String prefixMatch : PREFIX_MATCHES) {
            if (moduleName.startsWith(prefixMatch)) {
                return true;
            }
        }
        return false;
    }

    private static class ReconstructedModule {
        final String moduleName;
        File classesDir;
        File resourcesDir;
        List<String> expectedPackages;
        ModuleDescriptor moduleDescriptor;

        public ReconstructedModule(String moduleName) {
            this.moduleName = moduleName;
        }
    }

    /**
     * When we are in a development environment, IDEs like IntelliJ can sadly not reliably put the jar file
     * on the classpath.
     * They will always put the output directories on the classpath instead.
     * For our modules, this means that we have to resort to tricks.
     * First, we have to actually find all directories that make up a module.
     * Second, we have to build a module finder that is able to effectively build a union fs of these directories.
     */
    public static ModuleFinder recoverRequiredModulesFromDirectories(Collection<String> missingModules, List<File> directories, Set<File> claimedFiles) {
        var modules = new ArrayList<ReconstructedModule>(missingModules.size());
        for (var moduleName : missingModules) {
            modules.add(new ReconstructedModule(moduleName));
        }

        // For each missing module, we *need* the resource roots
        // If we find them, they contain a list of packages to expect
        for (File directory : directories) {
            for (var module : modules) {
                var resourceRootMarker = new File(directory, module.moduleName + ".resourceroot");
                if (resourceRootMarker.isFile()) {
                    // Should only use a resource dir for a single module, even if it theoretically can have multiple resource roots
                    module.expectedPackages = getExpectedPackages(resourceRootMarker);
                    module.resourcesDir = directory;
                    break;
                }
            }
        }

        // Now find class roots
        directoryLoop:
        for (var directory : directories) {
            // If the director has a module-info.class, it's quite clear what we have to do
            var moduleInfoFile = new File(directory, "module-info.class");
            if (moduleInfoFile.isFile()) {
                ModuleDescriptor moduleDescriptor;
                try (InputStream in = new FileInputStream(moduleInfoFile)) {
                    moduleDescriptor = ModuleDescriptor.read(in);
                } catch (IOException e) {
                    throw new FatalStartupException("Failed to read module descriptor " + moduleInfoFile);
                }

                // Does it match any of the modules we're loading?
                for (var module : modules) {
                    if (module.moduleName.equals(moduleDescriptor.name())) {
                        // It does! Jackpot.
                        module.classesDir = directory;
                        module.moduleDescriptor = moduleDescriptor;
                        continue directoryLoop;
                    }
                }
            }

            // Scan for all java packages in this directory
            Set<String> javaPackages;
            try {
                javaPackages = PackageCollectingVisitor.getJavaPackagesIn(directory);
            } catch (IOException e) {
                StartupLog.error("Failed to list directory contents of {}", directory);
                continue;
            }

            // The logic is as follows: If a directory contains all packages expected by a module
            // we consider it the class root.
            for (var module : modules) {
                if (module.classesDir == null && module.expectedPackages != null && !module.expectedPackages.isEmpty()) {
                    boolean allFound = true;

                    for (var expectedPackage : module.expectedPackages) {
                        if (!javaPackages.contains(expectedPackage)) {
                            allFound = false;
                            break;
                        }
                    }

                    if (allFound) {
                        // We consider this the classes root
                        module.classesDir = directory;
                        module.moduleDescriptor = buildInDevelopmentModuleDescriptor(module, javaPackages);
                        break;
                    }
                }
            }
        }

        // It is a fatal error if we're still missing some modules
        var stillMissingModules = new ArrayList<String>();
        for (var module : modules) {
            if (module.moduleDescriptor == null) {
                stillMissingModules.add(module.moduleName);
            }
        }
        if (!stillMissingModules.isEmpty()) {
            throw FatalStartupErrors.missingRequiredModules(stillMissingModules);
        }

        // Now for the painful work of constructing module finders
        var moduleRefs = new HashMap<String, ModuleReference>(modules.size());
        for (var module : modules) {
            if (module.classesDir == null) {
                continue;
            }

            var layeredDirs = new ArrayList<File>();
            layeredDirs.add(module.classesDir);
            claimedFiles.add(module.classesDir);

            if (module.resourcesDir != null && !module.resourcesDir.equals(module.classesDir)) {
                layeredDirs.add(module.resourcesDir);
                claimedFiles.add(module.resourcesDir);
            }

            var moduleReference = new ModuleReference(module.moduleDescriptor, module.classesDir.toURI()) {
                @Override
                public ModuleReader open() {
                    return new LayeredDirectoryModuleReader(layeredDirs);
                }
            };
            moduleRefs.put(module.moduleName, moduleReference);
        }

        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(moduleRefs.get(name));
            }

            @Override
            public Set<ModuleReference> findAll() {
                return Set.copyOf(moduleRefs.values());
            }
        };
    }

    /**
     * The JDK does not expose the functionality that we need.
     * Treat the given JAR file as a module as follows:
     * 1. The value of the Automatic-Module-Name attribute is the module name
     * We skip vesion: 2. The version, and the module name when the Automatic-Module-Name attribute is not present, is derived from the file ame of the JAR file
     * 3. All packages are derived from the .class files in the JAR file
     * 4. The contents of any META-INF/ services configuration files are mapped to "provides" declarations
     * We skip: 5. The Main-Class attribute in the main attributes of the JAR manifest is mapped to the module descriptor mainClass if possible
     * <p>
     * See jdk.internal.module.ModulePath#deriveModuleDescriptor(java.util.jar.JarFile)
     */
    private static @NotNull ModuleDescriptor buildInDevelopmentModuleDescriptor(ReconstructedModule module, Set<String> javaPackages) {
        var builder = ModuleDescriptor.newAutomaticModule(module.moduleName);
        builder.packages(Set.copyOf(javaPackages));

        var serviceFiles = new File(module.resourcesDir, "META-INF/services/").listFiles();
        if (serviceFiles != null) {
            for (var serviceFile : serviceFiles) {
                // Only consider files having the name of valid Java types
                if (JlsConstants.isTypeName(serviceFile.getName())) {
                    var implementationClasses = new ArrayList<String>();
                    try (var reader = new BufferedReader(new FileReader(serviceFile, StandardCharsets.UTF_8))) {
                        String line;
                        for (line = reader.readLine(); line != null; line = reader.readLine()) {
                            var commentStart = line.indexOf('#');
                            if (commentStart != -1) {
                                line = line.substring(0, commentStart);
                            }
                            var implClassName = line.trim();
                            if (!line.isEmpty()) {
                                var implPackage = JlsConstants.packageName(implClassName);
                                if (!javaPackages.contains(implPackage)) {
                                    throw new FatalStartupException(
                                            "Module " + module.moduleName + " provides service " + implClassName + " that is not in its own packages.");
                                }
                                implementationClasses.add(implClassName);
                            }
                        }
                    } catch (IOException e) {
                        throw new FatalStartupException("Failed to read service descriptor " + serviceFile);
                    }

                    if (!implementationClasses.isEmpty()) {
                        builder.provides(serviceFile.getName(), implementationClasses);
                    }
                }
            }
        }

        return builder.build();
    }

    private static List<String> getExpectedPackages(File resourceRootMarker) {
        var expectedPackages = new ArrayList<String>();
        try (var lineReader = new BufferedReader(new FileReader(resourceRootMarker))) {
            String line;
            while ((line = lineReader.readLine()) != null) {
                if (!line.isBlank()) {
                    expectedPackages.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource root marker " + resourceRootMarker, e);
        }
        return expectedPackages;
    }

    static class PackageCollectingVisitor extends SimpleFileVisitor<Path> {
        private final Set<String> packages;
        private final Path root;
        private String currentPackage = "";

        public PackageCollectingVisitor(Path root, Set<String> packages) {
            this.root = root;
            this.packages = packages;
        }

        public static Set<String> getJavaPackagesIn(File directory) throws IOException {
            var root = directory.toPath();
            var packages = new HashSet<String>();
            var visitor = new PackageCollectingVisitor(root, packages);
            Files.walkFileTree(root, visitor);
            return packages;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (dir.equals(root)) {
                return FileVisitResult.CONTINUE;
            }

            var directoryName = dir.getFileName().toString();

            // the JDK checks whether each segment of a package name is a valid java identifier
            // we perform this check on each directory name
            // See jdk.internal.module.Checks.isJavaIdentifier
            if (!JlsConstants.isJavaIdentifier(directoryName)) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            if (currentPackage.isEmpty()) {
                currentPackage = directoryName;
            } else {
                currentPackage += "." + directoryName;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (!dir.equals(root)) {
                var idx = currentPackage.lastIndexOf('.');
                if (idx == -1) {
                    currentPackage = "";
                } else {
                    currentPackage = currentPackage.substring(0, idx);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file.getFileName().toString().endsWith(".class")) {
                packages.add(currentPackage);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
