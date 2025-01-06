package cpw.mods.jarhandling;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record FolderModContainer(Path path) implements JarContents {
    private static final Logger LOG = LoggerFactory.getLogger(FolderModContainer.class);

    @Override
    public Path getPrimaryPath() {
        return path;
    }

    @Override
    public InputStream openFile(String relativePath) throws IOException {
        try {
            return Files.newInputStream(path.resolve(relativePath));
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public byte[] readFile(String relativePath) throws IOException {
        try {
            return Files.readAllBytes(path.resolve(relativePath));
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public boolean hasContentRoot(Path path) {
        return this.path.equals(path);
    }

    @Override
    public Stream<URL> getClasspathRoots() {
        try {
            return Stream.of(path.toUri().toURL());
        } catch (MalformedURLException e) {
            LOG.error("Failed to convert path to URL: {}", path, e);
            return Stream.of();
        }
    }

    @Override
    public void visitContent(ModContentVisitor visitor) {
        var startingPoint = path;
        try (var stream = Files.walk(startingPoint)) {
            stream.forEach(path -> {
                var file = path.toFile();
                if (file.isFile()) {
                    var relativePath = PathNormalization.normalize(startingPoint.relativize(path).toString());
                    visitor.visit(relativePath, () -> Files.newInputStream(path), () -> {
                        var attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
                        return new ModContentAttributes(attributes.lastModifiedTime(), attributes.size());
                    });
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk contents of " + path, e);
        }
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Override
    public Optional<URI> findFile(String relativePath) {
        if (relativePath.startsWith("/")) {
            throw new IllegalArgumentException("Must be a relative path: " + relativePath);
        }
        var pathToFile = path.resolve(relativePath);
        return pathToFile.toFile().isFile() ? Optional.of(pathToFile.toUri()) : Optional.empty();
    }

    @Override
    public void close() {}
}
