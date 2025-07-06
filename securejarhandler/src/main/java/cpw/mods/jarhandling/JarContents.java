package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.CompositeJarContents;
import cpw.mods.jarhandling.impl.FolderJarContents;
import cpw.mods.jarhandling.impl.JarFileContents;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Access to the contents of a list of {@link Path}s, interpreted as a jar file.
 * Typically used to build the {@linkplain JarMetadata metadata} for a {@link SecureJar}.
 * <p>
 * Convert to a full jar with {@link SecureJar#from(JarContents)}.
 */
@ApiStatus.NonExtendable
public sealed interface JarContents extends Closeable permits CompositeJarContents, FolderJarContents, JarFileContents, EmptyJarContents {
    @FunctionalInterface
    interface PathFilter {
        boolean test(String relativePath);
    }

    record FilteredPath(Path path, @Nullable CompositeJarContents.PathFilter filter) {
        public FilteredPath(Path path) {
            this(path, null);
        }
    }

    static JarContents ofFilteredPaths(Collection<FilteredPath> paths) throws IOException {
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Cannot construct jar contents without any paths.");
        } else {
            List<JarContents> contents = new ArrayList<>(paths.size());
            List<PathFilter> filters = new ArrayList<>(paths.size());
            for (var filteredPath : paths) {
                if (Files.exists(filteredPath.path)) {
                    contents.add(ofPath(filteredPath.path));
                    filters.add(filteredPath.filter);
                }
            }

            if (contents.isEmpty()) {
                throw new FileNotFoundException("At least one of the paths must exist when constructing jar contents: " + paths);
            } else if (contents.size() == 1 && filters.getFirst() == null) {
                return contents.getFirst(); // Uncommon case, but we'll still optimize for it
            }

            return new CompositeJarContents(contents, filters);
        }
    }

    static JarContents ofPaths(Collection<Path> paths) throws IOException {
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Cannot construct jar contents without any paths.");
        } else {
            List<JarContents> contents = new ArrayList<>(paths.size());
            for (var path : paths) {
                if (Files.exists(path)) {
                    contents.add(ofPath(path));
                }
            }

            if (contents.isEmpty()) {
                throw new FileNotFoundException("At least one of the paths must exist when constructing jar contents: " + paths);
            } else if (contents.size() == 1) {
                return contents.getFirst();
            }

            return new CompositeJarContents(contents);
        }
    }

    static JarContents ofPath(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return new JarFileContents(path);
        } else if (Files.isDirectory(path)) {
            return new FolderJarContents(path);
        } else {
            throw new FileNotFoundException("Cannot construct mod container from missing " + path);
        }
    }

    static JarContents empty(Path path) {
        return new EmptyJarContents(path);
    }

    Optional<String> getChecksum();

    /**
     * @see SecureJar#getPrimaryPath()
     */
    Path getPrimaryPath();

    /**
     * Does this mod container have the given file system path as one of its content roots?
     */
    boolean hasContentRoot(Path path);

    @Nullable
    default Manifest getJarManifest() {
        return new Manifest();
    }

    @Nullable
    default String getManifestAttribute(String name) {
        var manifest = getJarManifest();
        if (manifest != null) {
            return manifest.getMainAttributes().getValue(name);
        }
        return null;
    }

    @Nullable
    default String getManifestAttribute(Attributes.Name name) {
        var manifest = getJarManifest();
        if (manifest != null) {
            return manifest.getMainAttributes().getValue(name);
        }
        return null;
    }

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
     * Visits all content found in this jar.
     */
    default void visitContent(JarResourceVisitor visitor) {
        visitContent("", visitor);
    }

    /**
     * Visits all content found in this jar, starting in the given folder
     */
    void visitContent(String startingFolder, JarResourceVisitor visitor);
}
