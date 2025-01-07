package cpw.mods.jarhandling;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public final class CompositeModContainer implements JarContents {
    private final JarContents[] delegates;
    @Nullable
    private final PathFilter[] filters;

    public CompositeModContainer(List<JarContents> delegates) {
        this(delegates, null);
    }

    public CompositeModContainer(List<JarContents> delegates, @Nullable List<PathFilter> filters) {
        this.delegates = delegates.toArray(JarContents[]::new);
        this.filters = filters != null ? filters.toArray(PathFilter[]::new) : null;
        if (delegates.isEmpty()) {
            throw new IllegalArgumentException("Cannot construct an empty mod container");
        }
        if (filters != null && delegates.size() != filters.size()) {
            throw new IllegalArgumentException("The number of delegates and filters must match.");
        }
    }

    @Override
    public Path getPrimaryPath() {
        return delegates[0].getPrimaryPath();
    }

    @Override
    public String toString() {
        return "[" + Arrays.stream(delegates).map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public Optional<URI> findFile(String relativePath) {
        for (var delegate : delegates) {
            var result = delegate.findFile(relativePath);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    @Nullable
    @Override
    public Manifest getJarManifest() {
        for (var delegate : delegates) {
            var manifest = delegate.getJarManifest();
            if (manifest != null) {
                return manifest;
            }
        }
        return null;
    }

    @Override
    public InputStream openFile(String relativePath) throws IOException {
        for (var delegate : delegates) {
            var stream = delegate.openFile(relativePath);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    @Override
    public byte[] readFile(String relativePath) throws IOException {
        for (var delegate : delegates) {
            var content = delegate.readFile(relativePath);
            if (content != null) {
                return content;
            }
        }
        return null;
    }

    @Override
    public boolean hasContentRoot(Path path) {
        for (var delegate : delegates) {
            if (delegate.hasContentRoot(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Stream<URL> getClasspathRoots() {
        return Arrays.stream(delegates).flatMap(JarContents::getClasspathRoots);
    }

    @Override
    public void visitContent(ModContentVisitor visitor) {
        // This is based on the logic that openResource will return the file from the *first* delegate
        // Every relative path we return will not be returned again
        var distinctVisitor = new ModContentVisitor() {
            final Set<String> pathsVisited = new HashSet<>();

            @Override
            public void visit(String relativePath, IOSupplier<InputStream> contentSupplier, IOSupplier<ModContentAttributes> attributesSupplier) {
                if (pathsVisited.add(relativePath)) {
                    visitor.visit(relativePath, contentSupplier, attributesSupplier);
                }
            }
        };

        for (var delegate : delegates) {
            delegate.visitContent(distinctVisitor);
        }
    }

    @Override
    public void close() throws IOException {
        IOException error = null;
        for (var delegate : delegates) {
            try {
                delegate.close();
            } catch (IOException e) {
                if (error == null) {
                    error = new IOException("Failed to close one ore more delegates of " + this);
                }
                error.addSuppressed(e);
            }
        }
        if (error != null) {
            throw error;
        }
    }
}
