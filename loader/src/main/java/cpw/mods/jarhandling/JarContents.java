package cpw.mods.jarhandling;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;
import org.jetbrains.annotations.Nullable;

public sealed interface JarContents extends AutoCloseable permits CompositeModContainer, FolderModContainer, JarModContainer, EmptyModContainer {
    record FilteredPath(Path path, @Nullable CompositeModContainer.PathFilter filter) {}

    static JarContents ofFilteredPaths(Collection<FilteredPath> paths) throws IOException {
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Cannot construct an empty mod container.");
        } else {
            try {
                var containers = paths.stream().map(path -> {
                    try {
                        return JarContents.ofPath(path.path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).toList();
                var filters = paths.stream().map(FilteredPath::filter).toList();
                return new CompositeModContainer(containers, filters);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }

    static JarContents ofPaths(Collection<Path> paths) throws IOException {
        if (paths.size() == 1) {
            return ofPath(paths.iterator().next());
        } else if (paths.size() > 1) {
            try {
                return new CompositeModContainer(paths.stream().map(path -> {
                    if (!Files.exists(path)) {
                        return empty(path);
                    }
                    try {
                        return JarContents.ofPath(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).toList());
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        } else {
            throw new IllegalArgumentException("Cannot construct an empty mod container.");
        }
    }

    static JarContents ofPath(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return new JarModContainer(path);
        } else if (Files.isDirectory(path)) {
            return new FolderModContainer(path);
        } else {
            throw new ModFileLoadingException("Cannot construct mod container from missing " + path);
        }
    }

    static JarContents empty(Path path) {
        return new EmptyModContainer(path);
    }

    Optional<URI> findFile(String relativePath);

    Path getPrimaryPath();

    @Nullable
    default Manifest getJarManifest() {
        return null;
    }

    @Nullable
    InputStream openFile(String relativePath) throws IOException;

    byte @Nullable [] readFile(String relativePath) throws IOException;

    /**
     * Does this mod container have the given file system path as one of its content roots?
     */
    boolean hasContentRoot(Path path);

    /**
     * @return The roots of this mod-container for use in an {@link java.net.URLClassLoader}.
     */
    Stream<URL> getClasspathRoots();

    /**
     * Visits all content found in this container.
     */
    void visitContent(ModContentVisitor visitor);

    @Override
    void close() throws IOException;

    @FunctionalInterface
    interface PathFilter {
        boolean test(String relativePath);
    }

    static Builder builder() {
        return new Builder();
    }

    final class Builder {
        private final List<FilteredPath> paths = new ArrayList<>();

        private Builder() {}

        public Builder path(Path path) {
            return this;
        }

        public Builder filteredPath(Path path, PathFilter filter) {
            return this;
        }

        public JarContents build() throws IOException {
            if (paths.isEmpty()) {
                // return EMPTY; // TODO
                throw new IllegalArgumentException();
            }
            if (paths.size() == 1 && paths.getFirst().filter == null) {
                return ofPath(paths.getFirst().path);
            }
            return ofFilteredPaths(paths);
        }
    }
}
