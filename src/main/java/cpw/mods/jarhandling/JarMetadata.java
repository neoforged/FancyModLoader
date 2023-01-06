package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.ModuleJarMetadata;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public interface JarMetadata {
    String name();
    String version();
    ModuleDescriptor descriptor();
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
        "abstract","continue","for","new","switch","assert",
        "default","goto","package","synchronized","boolean",
        "do","if","private","this","break","double","implements",
        "protected","throw","byte","else","import","public","throws",
        "case","enum","instanceof","return","transient","catch",
        "extends","int","short","try","char","final","interface",
        "static","void","class","finally","long","strictfp",
        "volatile","const","float","native","super","while");
    Pattern KEYWORD_PARTS = Pattern.compile("(?<=^|\\.)(" + String.join("|", ILLEGAL_KEYWORDS) + ")(?=\\.|$)");

    static JarMetadata from(final SecureJar jar, final Path... path) {
        if (path.length==0) throw new IllegalArgumentException("Need at least one path");
        final var pkgs = jar.getPackages();
        var mi = jar.moduleDataProvider().findFile("module-info.class");
        if (mi.isPresent()) {
            return new ModuleJarMetadata(mi.get(), pkgs);
        } else {
            var providers = jar.getProviders();
            var fileCandidate = fromFileName(path[0], pkgs, providers);
            var aname = jar.moduleDataProvider().getManifest().getMainAttributes().getValue("Automatic-Module-Name");
            if (aname != null) {
                return new SimpleJarMetadata(aname, fileCandidate.version(), pkgs, providers);
            } else {
                return fileCandidate;
            }
        }
    }
    static SimpleJarMetadata fromFileName(final Path path, final Set<String> pkgs, final List<SecureJar.Provider> providers) {

        // detect Maven-like paths
        Path versionMaybe = path.getParent();
        if (versionMaybe != null)
        {
            Path artifactMaybe = versionMaybe.getParent();
            if (artifactMaybe != null)
            {
                Path artifactNameMaybe = artifactMaybe.getFileName();
                if (artifactNameMaybe != null && path.getFileName().toString().startsWith(artifactNameMaybe + "-" + versionMaybe.getFileName().toString())) {
                    var name = artifactMaybe.getFileName().toString();
                    var ver = versionMaybe.getFileName().toString();
                    var mat = MODULE_VERSION.matcher(ver);
                    if (mat.find()) {
                        var potential = ver.substring(mat.start());
                        ver = safeParseVersion(potential, path.getFileName().toString());
                        return new SimpleJarMetadata(cleanModuleName(name), ver, pkgs, providers);
                    } else {
                        return new SimpleJarMetadata(cleanModuleName(name), null, pkgs, providers);
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
            return new SimpleJarMetadata(cleanModuleName(name), ver, pkgs, providers);
        } else {
            return new SimpleJarMetadata(cleanModuleName(fn), null, pkgs, providers);
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
        if (len > 0 && mn.charAt(len-1) == '.')
            mn = TRAILING_DOTS.matcher(mn).replaceAll("");

        // fixup digits-only components
        mn = NUMBERLIKE_PARTS.matcher(mn).replaceAll("_$1");

        // fixup keyword components
        mn = KEYWORD_PARTS.matcher(mn).replaceAll("_$1");

        return mn;
    }
}
