package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Utilities for creating {@link java.lang.module.ModuleDescriptor} from {@link cpw.mods.jarhandling.JarContents}.
 * <p>
 * Most of this code also lives in the JDK in the internal ModulePath class.
 */
@ApiStatus.Internal
public final class ModuleDescriptorFactory {
    private ModuleDescriptorFactory() {}

    /**
     * Scans an automatic module for the following and applies them to the given module builder.
     *
     * <ul>
     * <li>Packages containing class files.</li>
     * <li>Java {@link java.util.ServiceLoader} providers found in {@code META-INF/services/}</li>
     * </ul>
     *
     * @param excludedRootDirectories Allows for additional root directories to be completely ignored for scanning
     *                                packages. Useful if it is known beforehand that certain subdirectories
     *                                are unlikely to contain classes.
     */
    public static void scanAutomaticModule(
            JarContents jar,
            ModuleDescriptor.Builder builder,
            String... excludedRootDirectories) {
        Set<String> ignoredRootDirs = Set.of(excludedRootDirectories);
        var root = ((JarContentsImpl) jar).filesystem.getRoot();

        var metaInfDir = root.resolve("META-INF");
        var servicesDir = root.resolve("META-INF/services");

        Set<String> packageNames = new HashSet<>();
        List<Path> serviceProviderFiles = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.startsWith(metaInfDir)) {
                        if (file.startsWith(servicesDir)) {
                            var serviceName = file.getFileName().toString();
                            // Ignore files in META-INF/services/ whose filenames are not valid Java class names
                            if (JlsConstants.isTypeName(serviceName)) {
                                serviceProviderFiles.add(file);
                            }
                        }
                        // Ignore all content beneath META-INF except for service files
                        return FileVisitResult.CONTINUE;
                    }

                    // In automatic modules, only packages with .class files are considered,
                    // unlike with normal modules where resources would also be scanned.
                    if (file.getFileName().toString().endsWith(".class")) {
                        String relativeDir = root.relativize(file.getParent()).toString();
                        var pkg = relativeDir.replace('/', '.');
                        if (!pkg.isEmpty()) {
                            packageNames.add(pkg);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                    // Always read META-INF/services
                    if (path.startsWith(servicesDir) && path.getNameCount() <= servicesDir.getNameCount()) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (path.getNameCount() > 0) {
                        // Skip if ignored root
                        if (!ignoredRootDirs.isEmpty() && ignoredRootDirs.contains(path.getName(0).toString())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        // Skip, if not a valid java package name
                        for (int i = 0; i < path.getNameCount(); i++) {
                            var segment = path.getName(i).toString();
                            if (!JlsConstants.isJavaIdentifier(segment)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan for packages and services in " + jar, e);
        }
        builder.packages(packageNames);

        for (Path serviceProviderFile : serviceProviderFiles) {
            try {
                parseServiceFile(serviceProviderFile, packageNames, builder);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to parse service provider file " + serviceProviderFile + " in " + jar, e);
            }
        }
    }

    /**
     * Parses a Java ServiceLoader file and adds its content as a provided service to the given module
     * descriptor builder.
     * <p>Equivalent to the code found in ModulePath#deriveModuleDescriptor(JarFile)
     */
    private static void parseServiceFile(Path file, Set<String> packageNames, ModuleDescriptor.Builder builder) throws IOException {
        var serviceName = file.getFileName().toString();

        List<String> providerClasses = new ArrayList<>();
        try (var in = Files.newInputStream(file)) {
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                // Strip comments
                var startOfComment = line.indexOf('#');
                if (startOfComment != -1) {
                    line = line.substring(0, startOfComment);
                }
                line = line.trim(); // Trim whitespace *after* removing the comment

                // We're parsing service files after scanning for packages,
                // which means we can validate at this point that a service provider
                // only provides a class that is contained in the Jar file.
                if (!line.isEmpty()) {
                    String packageName = JlsConstants.getPackageName(line);
                    if (!packageNames.contains(packageName)) {
                        String msg = "Service provider file " + file + " contains service that is not in this Jar file: " + line;
                        throw new InvalidModuleDescriptorException(msg);
                    }
                    providerClasses.add(line);
                }
            }
        }

        if (!providerClasses.isEmpty()) {
            builder.provides(serviceName, providerClasses);
        }
    }

    /**
     * Scans a given Jar for all packages that contain files for use with {@link ModuleDescriptor#read}.
     * <p>Unlike {@link #scanAutomaticModule}, this also finds packages that contain only resource files, which is
     * consistent with the behavior of {@link java.lang.module.ModuleFinder} for modular Jar files.
     */
    public static Set<String> scanModulePackages(JarContents jar) {
        var root = ((JarContentsImpl) jar).filesystem.getRoot();

        Set<String> packageNames = new HashSet<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        var pkg = root.relativize(file.getParent()).toString().replace('/', '.');
                        if (!pkg.isEmpty()) {
                            packageNames.add(pkg);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                    if (path.getNameCount() > 0) {
                        // Skip, if not a valid java package name
                        for (int i = 0; i < path.getNameCount(); i++) {
                            var segment = path.getName(i).toString();
                            if (!JlsConstants.isJavaIdentifier(segment)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return Set.copyOf(packageNames);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan for packages in " + jar, e);
        }
    }
}
