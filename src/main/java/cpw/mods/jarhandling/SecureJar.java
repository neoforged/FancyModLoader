package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.jarhandling.impl.JarContentsImpl;
import cpw.mods.niofs.union.UnionPathFilter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * A secure jar is the full definition for a module,
 * including all its paths and code signing metadata.
 */
public interface SecureJar {
    /**
     * Creates a jar from a list of paths.
     * See {@link JarContentsBuilder} for more configuration options.
     */
    static SecureJar from(final Path... paths) {
        return from(new JarContentsBuilder().paths(paths).build());
    }

    /**
     * Creates a jar from its contents, with default metadata.
     */
    static SecureJar from(JarContents contents) {
        return from(contents, JarMetadata.from(contents));
    }

    /**
     * Creates a jar from its contents and metadata.
     */
    static SecureJar from(JarContents contents, JarMetadata metadata) {
        return new Jar((JarContentsImpl) contents, metadata);
    }

    ModuleDataProvider moduleDataProvider();

    /**
     * A {@link SecureJar} can be built from multiple paths, either to directories or to {@code .jar} archives.
     * This function returns the first of these paths, either to a directory or to an archive file.
     *
     * <p>This is generally used for reporting purposes,
     * for example to obtain a human-readable single location for this jar.
     */
    Path getPrimaryPath();

    /**
     * {@return the signers of the manifest, or {@code null} if the manifest is not signed}
     */
    @Nullable
    CodeSigner[] getManifestSigners();

    Status verifyPath(Path path);

    Status getFileStatus(String name);

    @Nullable
    Attributes getTrustedManifestEntries(String name);

    boolean hasSecurityData();

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

        /**
         * {@return the signers if the class name can be verified, or {@code null} otherwise}
         */
        @Nullable
        CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes);
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
                        .filter(l-> !l.isEmpty() && !l.startsWith("#")) // We support comments :)
                        .filter(p-> pkgFilter == null || pkgFilter.test(p.replace('.','/'), path.getRoot()))
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
