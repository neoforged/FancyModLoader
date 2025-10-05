/*
 * See LICENSE-securejarhandler for licensing details.
 */

package net.neoforged.fml.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import net.neoforged.fml.jarcontents.JarContents;
import org.jetbrains.annotations.Nullable;

/**
 * A secure jar is the full definition for a module,
 * including all its paths and code signing metadata.
 */
public interface SecureJar {
    /**
     * Creates a jar from a list of paths.
     * See {@link JarContents} for more configuration options.
     */
    static SecureJar from(Path... paths) throws IOException {
        return from(JarContents.ofPaths(List.of(paths)));
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
        return new Jar(contents, metadata);
    }

    JarContents contents();

    ModuleDataProvider moduleDataProvider();

    /**
     * A {@link SecureJar} can be built from multiple paths, either to directories or to {@code .jar} archives.
     * This function returns the first of these paths, either to a directory or to an archive file.
     *
     * <p>This is generally used for reporting purposes,
     * for example to obtain a human-readable single location for this jar.
     */
    Path getPrimaryPath();

    String name();

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
        Optional<InputStream> open(String name);

        /**
         * {@return the manifest of the jar}
         */
        Manifest getManifest();
    }
}
