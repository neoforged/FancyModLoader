package cpw.mods.jarhandling;

import cpw.mods.niofs.union.UnionPathFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

/**
 * A secure jar is the full definition for a module,
 * including all its paths and code signing metadata.
 */
public interface SecureJar {
    ModuleDataProvider moduleDataProvider();

    /**
     * A {@link SecureJar} can be built from multiple paths, either to directories or to {@code .jar} archives.
     * This function returns the first of these paths, either to a directory or to an archive file.
     *
     * <p>This is generally used for reporting purposes,
     * for example to obtain a human-readable single location for this jar.
     */
    Path getPrimaryPath();

    JarContents container();

    String name();

    Path getPath(String first, String... rest);

    /**
     * {@return the root path in the jar's own filesystem}
     */
    Path getRootPath();

    /**
     * Closes the underlying file system resources (if any).
     * Renders this object unusable.
     */
    void close() throws IOException;

    /**
     * All the functions that are necessary to turn a {@link SecureJar} into a module.
     */
    interface ModuleDataProvider {
        /**
         * {@return the name of the module}
         */
        String name();

        /**
         * {@return the descriptor of the module}
         */
        ModuleDescriptor descriptor();

        /**
         * @see ModuleReference#location()
         */
        @Nullable
        URI uri();

        /**
         * @see ModuleReader#find(String)
         */
        Optional<URI> findFile(String name);

        /**
         * @see ModuleReader#open(String)
         */
        Optional<InputStream> open(final String name);

        /**
         * {@return the manifest of the jar}
         */
        Manifest getManifest();
    }

    /**
     * Same as {@link ModuleDescriptor.Provides}, but with an exposed constructor.
     * Use only if the {@link #fromPath} method is useful to you.
     */
    record Provider(String serviceName, List<String> providers) {
        /**
         * Helper method to parse service provider implementations from a {@link Path}.
         */
        public static Provider fromPath(final Path path, final UnionPathFilter pkgFilter) {
            final var sname = path.getFileName().toString();
            try {
                var entries = Files.readAllLines(path).stream()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#")) // We support comments :)
                        .filter(p -> pkgFilter == null || pkgFilter.test(p.replace('.', '/'), path.getRoot()))
                        .toList();
                return new Provider(sname, entries);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    enum Status {
        NONE, INVALID, UNVERIFIED, VERIFIED
    }
}
