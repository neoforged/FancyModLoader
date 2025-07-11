package cpw.mods.jarhandling;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.jar.Manifest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
     * Tries to open a file inside the jar content using a path relative to the root.
     * <p>
     * The stream will not be buffered.
     * 
     * @return null if the file cannot be found, or if there is a directory with the given name.
     */
    @Nullable
    InputStream getResourceAsStream(String name) throws IOException;

    /**
     * {@return the manifest of the jar}
     * Empty if no manifest is present in the jar.
     */
    Manifest getManifest();

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
