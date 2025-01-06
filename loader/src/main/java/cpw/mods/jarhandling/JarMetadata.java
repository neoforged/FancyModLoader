package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.ModuleJarMetadata;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public interface JarMetadata {
    String name();

    @Nullable
    String version();

    ModuleDescriptor descriptor();

    /**
     * {@return the provider declarations for this jar}
     *
     * <p>Computing the {@link #descriptor()} can be expensive as it requires scanning the jar for packages.
     * If only the service providers are needed, this method can be used instead.
     */
    default List<SecureJar.Provider> providers() {
        return descriptor().provides().stream().map(p -> new SecureJar.Provider(p.service(), p.providers())).toList();
    }

    // ALL from jdk.internal.module.ModulePath.java
    Pattern DASH_VERSION = Pattern.compile("-([.\\d]+)");
    Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");
    Pattern REPEATING_DOTS = Pattern.compile("(\\.)(\\1)+");
    Pattern LEADING_DOTS = Pattern.compile("^\\.");
    Pattern TRAILING_DOTS = Pattern.compile("\\.$");
    // Extra sanitization
    Pattern MODULE_VERSION = Pattern.compile("(?<=^|-)([\\d][.\\d]*)");
    Pattern NUMBERLIKE_PARTS = Pattern.compile("(?<=^|\\.)([0-9]+)"); // matches asdf.1.2b because both are invalid java identifiers
    List<String> ILLEGAL_KEYWORDS = List.of(
            "abstract", "continue", "for", "new", "switch", "assert",
            "default", "goto", "package", "synchronized", "boolean",
            "do", "if", "private", "this", "break", "double", "implements",
            "protected", "throw", "byte", "else", "import", "public", "throws",
            "case", "enum", "instanceof", "return", "transient", "catch",
            "extends", "int", "short", "try", "char", "final", "interface",
            "static", "void", "class", "finally", "long", "strictfp",
            "volatile", "const", "float", "native", "super", "while");
    Pattern KEYWORD_PARTS = Pattern.compile("(?<=^|\\.)(" + String.join("|", ILLEGAL_KEYWORDS) + ")(?=\\.|$)");

    /**
     * Builds the jar metadata for a jar following the normal rules for Java jars.
     *
     * <p>If the jar has a {@code module-info.class} file, the module info is read from there.
     * Otherwise, the jar is an automatic module, whose name is optionally derived
     * from {@code Automatic-Module-Name} in the manifest.
     */
    static JarMetadata from(JarContents jar) throws IOException {
        var packages = new HashSet<String>();
        var serviceProviders = new ArrayList<SecureJar.Provider>();
        indexJarContent(jar, packages, serviceProviders);

        try (var moduleInfoIn = jar.openFile("module-info.class")) {
            if (moduleInfoIn != null) {
                return new ModuleJarMetadata(moduleInfoIn, () -> packages);
            }

            var nav = computeNameAndVersion(jar.getPrimaryPath());
            String name = nav.name();
            String version = nav.version();

            Manifest jarManifest = jar.getJarManifest();
            if (jarManifest != null) {
                String automaticModuleName = jarManifest.getMainAttributes().getValue("Automatic-Module-Name");
                if (automaticModuleName != null) {
                    name = automaticModuleName;
                }
            }

            return new SimpleJarMetadata(name, version, () -> packages, serviceProviders);
        }
    }

    static void indexJarContent(JarContents jar, Set<String> packages, List<SecureJar.Provider> serviceProviders) {
        jar.visitContent((relativePath, contentSupplier, attributesSupplier) -> {
            if (relativePath.startsWith("META-INF/services/")) {
                var serviceClass = relativePath.substring("META-INF/services/".length());
                if (serviceClass.contains("/")) {
                    return; // In some subdirectory under META-INF/services/, this is not a real service file
                }

                var implementationClasses = new ArrayList<String>();
                try (var reader = new BufferedReader(new InputStreamReader(contentSupplier.get()))) {
                    for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                        var soc = line.indexOf('#');
                        if (soc != -1) {
                            line = line.substring(0, soc);
                        }
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        // NOTE: This differs from previous iterations of SecureJar in that we do not filter the
                        // impl-class against the JarContents filters.
                        // Whoever builds the Jar is responsible for only making service manifests
                        // visible that are actually valid with the filter in place.
                        implementationClasses.add(line);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read service file " + relativePath + " from " + jar, e);
                }

                serviceProviders.add(new SecureJar.Provider(serviceClass, implementationClasses));

            } else if (relativePath.contains("/") && relativePath.endsWith(".class")) {
                var segments = relativePath.split("/");

                // the JDK checks whether each segment of a package name is a valid java identifier
                // we perform this check on each directory name
                // See jdk.internal.module.Checks.isJavaIdentifier
                for (var segment : segments) {
                    if (!JlsConstants.isJavaIdentifier(segment)) {
                        return; // If any segment is not a valid java identifier, we skip the package name
                    }
                }

                var packageName = new StringBuilder();
                for (int i = 0; i < segments.length - 1; i++) {
                    if (i != 0) {
                        packageName.append('.');
                    }
                    packageName.append(segments[i]);
                }
                packages.add(packageName.toString());
            }
        });
    }

    private static NameAndVersion computeNameAndVersion(Path path) {
        // detect Maven-like paths
        Path versionMaybe = path.getParent();
        if (versionMaybe != null) {
            Path artifactMaybe = versionMaybe.getParent();
            if (artifactMaybe != null) {
                Path artifactNameMaybe = artifactMaybe.getFileName();
                if (artifactNameMaybe != null && path.getFileName().toString().startsWith(artifactNameMaybe + "-" + versionMaybe.getFileName().toString())) {
                    var name = artifactMaybe.getFileName().toString();
                    var ver = versionMaybe.getFileName().toString();
                    var mat = MODULE_VERSION.matcher(ver);
                    if (mat.find()) {
                        var potential = ver.substring(mat.start());
                        ver = safeParseVersion(potential, path.getFileName().toString());
                        return new NameAndVersion(cleanModuleName(name), ver);
                    } else {
                        return new NameAndVersion(cleanModuleName(name), null);
                    }
                }
            }
        }

        // fallback parsing
        var fn = path.getFileName().toString();
        var lastDot = fn.lastIndexOf('.');
        if (lastDot > 0) {
            fn = fn.substring(0, lastDot); // strip extension if possible
        }

        var mat = DASH_VERSION.matcher(fn);
        if (mat.find()) {
            var potential = fn.substring(mat.start() + 1);
            var ver = safeParseVersion(potential, path.getFileName().toString());
            var name = mat.replaceAll("");
            return new NameAndVersion(cleanModuleName(name), ver);
        } else {
            return new NameAndVersion(cleanModuleName(fn), null);
        }
    }

    private static String safeParseVersion(String ver, String filename) {
        try {
            var len = ver.length();
            if (len == 0)
                throw new IllegalArgumentException("Error parsing version info from " + filename + ": Empty Version String");

            var last = ver.charAt(len - 1);
            if (last == '.' || last == '+' || last == '-') { //Attempt to filter out the common wrong file names.
                if (len == 1)
                    throw new IllegalArgumentException("Error parsing version info from " + filename + ": Invalid version \"" + ver + "\"");
                ver = ver.substring(0, len - 1);
            }

            return ModuleDescriptor.Version.parse(ver).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error parsing version info from " + filename + " (" + ver + "): " + e.getMessage(), e);
        }
    }

    private static String cleanModuleName(String mn) {
        // replace non-alphanumeric
        mn = NON_ALPHANUM.matcher(mn).replaceAll(".");

        // collapse repeating dots
        mn = REPEATING_DOTS.matcher(mn).replaceAll(".");

        // drop leading dots
        if (!mn.isEmpty() && mn.charAt(0) == '.')
            mn = LEADING_DOTS.matcher(mn).replaceAll("");

        // drop trailing dots
        int len = mn.length();
        if (len > 0 && mn.charAt(len - 1) == '.')
            mn = TRAILING_DOTS.matcher(mn).replaceAll("");

        // fixup digits-only components
        mn = NUMBERLIKE_PARTS.matcher(mn).replaceAll("_$1");

        // fixup keyword components
        mn = KEYWORD_PARTS.matcher(mn).replaceAll("_$1");

        return mn;
    }
}
