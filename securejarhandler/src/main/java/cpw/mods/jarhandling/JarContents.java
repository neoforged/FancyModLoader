package cpw.mods.jarhandling;

import java.io.Closeable;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import org.jetbrains.annotations.ApiStatus;

/**
 * Access to the contents of a list of {@link Path}s, interpreted as a jar file.
 * Typically used to build the {@linkplain JarMetadata metadata} for a {@link SecureJar}.
 *
 * <p>Create with {@link JarContentsBuilder}.
 * Convert to a full jar with {@link SecureJar#from(JarContents)}.
 */
@ApiStatus.NonExtendable
public interface JarContents extends Closeable {
    /**
     * @see SecureJar#getPrimaryPath()
     */
    Path getPrimaryPath();

    /**
     * Looks for a file in the jar.
     */
    Optional<URI> findFile(String name);

    /**
     * {@return the manifest of the jar}
     * Empty if no manifest is present in the jar.
     */
    Manifest getManifest();

    /**
     * {@return all the packages in the jar}
     * (Every folder containing a {@code .class} file is considered a package.)
     */
    Set<String> getPackages();

    /**
     * {@return all the packages in the jar, with some root packages excluded}
     *
     * <p>This can be used to skip scanning of folders that are known to not contain code,
     * but would be expensive to go through.
     */
    Set<String> getPackagesExcluding(String... excludedRootPackages);

    /**
     * Parses the {@code META-INF/services} files in the jar, and returns the list of service providers.
     */
    List<SecureJar.Provider> getMetaInfServices();

    /**
     * Create plain jar contents from a single jar file or folder.
     * For more advanced use-cases see {@link JarContentsBuilder}.
     */
    static JarContents of(Path fileOrFolder) {
        return new JarContentsBuilder().paths(fileOrFolder).build();
    }

    /**
     * Create a virtual jar that consists of the contents of the given jar-files and folders.
     * For more advanced use-cases see {@link JarContentsBuilder}.
     */
    static JarContents of(Collection<Path> filesOrFolders) {
        return new JarContentsBuilder().paths(filesOrFolders.toArray(new Path[0])).build();
    }
}
