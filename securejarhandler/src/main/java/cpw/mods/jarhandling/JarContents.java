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
 *
 * <h2>Relative Paths</h2>
 * To address files within jars, paths that are interpreted to be relative to the root of the jar file are used.
 * The relative path for a class-file for class {@code your.package.YourClass} would be {@code your/package/YourClass},
 * for example.
 */
@ApiStatus.NonExtendable
public interface JarContents extends Closeable {
    /**
     * @see SecureJar#getPrimaryPath()
     */
    Path getPrimaryPath();

    /**
     * Returns locations that form the content roots of this jar content.
     * <p>The resulting paths do not need to be on the local file-system, they can be from custom NIO filesystem implementations.
     * <p>The returned list may also not contain all content roots if the the underlying jar content is held in-memory.
     */
    Collection<Path> getContentRoots();

    /**
     * Tries to find a resource with the given path in this jar content.
     *
     * @param relativePath See {@link JarContents} for a definition of relative paths.
     * @return Null if the resource could not be found within the jar.
     */
    @Nullable
    JarResource get(String relativePath);

    /**
     * Looks for a file in the jar.
     */
    Optional<URI> findFile(String name);

    /**
     * Tries to open a file inside the jar content using a path relative to the root.
     * <p>The stream will not be buffered.
     * <p>The behavior when {@code relativePath} refers to a directory rather than a file is unspecified. The
     * method may throw a {@code IOException} immediately, but may also defer this until the first byte is
     * read from the stream. This behavior is filesystem provider specific.
     *
     * @return null if the file cannot be found
     */
    @Nullable
    InputStream openFile(String relativePath) throws IOException;

    /**
     * A convenience method that {@linkplain #openFile(String) opens a file} and if the file was found,
     * returns its content.
     * <p>Trying to read the contents of a directory using this method will result in an {@code IOException}.
     *
     * @return Null if the file does not exist.
     */
    default byte @Nullable [] readFile(String relativePath) throws IOException {
        try (var in = openFile(relativePath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    /**
     * Checks, if a given file exists in this jar.
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
     * <p><strong>NOTE:</strong> Do not modify the returned manifest.
     */
    Manifest getManifest();

    /**
     * Visits all content found in this jar.
     */
    default void visitContent(JarResourceVisitor visitor) {
        visitContent("", visitor);
    }

    /**
     * Visits all content found in this jar, starting in the given folder.
     * <p>If the folder does not exist, the visitor is not invoked and no error is raised.
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
