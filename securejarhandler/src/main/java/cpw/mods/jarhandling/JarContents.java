package cpw.mods.jarhandling;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.jar.Attributes;
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
     * Does this mod container have the given file system path as one of its content roots?
     */
    boolean hasContentRoot(Path path);

    @Nullable
    String getManifestAttribute(String name);

    @Nullable
    String getManifestAttribute(Attributes.Name name);

    @Nullable
    JarResource get(String relativePath);

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
    InputStream openFile(String name) throws IOException;

    default byte @Nullable [] readFile(String relativePath) throws IOException {
        try (var in = openFile(relativePath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    /**
     * Checks, if a given file exists in this jar file.
     *
     * @param relativePath The path to the file, relative to the root of this Jar file.
     * @return True if the file exists, false if it doesn't or the given path denotes a directory.
     */
    default boolean containsFile(String relativePath) {
        try {
            var stream = openFile(relativePath);
            if (stream != null) {
                stream.close();
                return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    /**
     * {@return the manifest of the jar}
     * Empty if no manifest is present in the jar.
     */
    @Deprecated
    Manifest getManifest();

    /**
     * Visits all content found in this jar.
     */
    default void visitContent(JarResourceVisitor visitor) {
        visitContent("", visitor);
    }

    /**
     * Visits all content found in this jar, starting in the given folder
     */
    void visitContent(String startingFolder, JarResourceVisitor visitor);

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
