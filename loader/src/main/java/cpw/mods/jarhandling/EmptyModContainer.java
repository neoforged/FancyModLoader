package cpw.mods.jarhandling;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public final class EmptyModContainer implements JarContents {
    private final Path path;

    public EmptyModContainer(Path path) {
        this.path = path;
    }

    @Override
    public Optional<URI> findFile(String relativePath) {
        return Optional.empty();
    }

    @Override
    public Path getPrimaryPath() {
        return path;
    }

    @Override
    public @Nullable InputStream openFile(String relativePath) throws IOException {
        return null;
    }

    @Override
    public byte @Nullable [] readFile(String relativePath) throws IOException {
        return null;
    }

    @Override
    public boolean hasContentRoot(Path path) {
        return false;
    }

    @Override
    public Stream<URL> getClasspathRoots() {
        return Stream.empty();
    }

    @Override
    public void visitContent(ModContentVisitor visitor) {}

    @Override
    public void close() throws IOException {}
}
