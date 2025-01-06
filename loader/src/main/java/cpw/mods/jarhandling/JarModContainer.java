package cpw.mods.jarhandling;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JarModContainer implements JarContents {
    private static final Logger LOG = LoggerFactory.getLogger(JarModContainer.class);

    private final Path path;
    private final JarFile jarFile;
    private final Manifest jarManifest;

    JarModContainer(Path path) throws IOException {
        this.path = path;
        this.jarFile = new JarFile(path.toFile());
        this.jarManifest = this.jarFile.getManifest();
    }

    @Override
    public Optional<URI> findFile(String relativePath) {
        var entry = jarFile.getEntry(relativePath);
        if (entry != null && !entry.isDirectory()) {
            return Optional.of(URI.create("jar:" + path.toUri() + "!/" + relativePath));
        }
        return Optional.empty();
    }

    @Override
    public Path getPrimaryPath() {
        return path;
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Nullable
    @Override
    public Manifest getJarManifest() {
        return jarManifest;
    }

    @Override
    public InputStream openFile(String relativePath) throws IOException {
        var entry = jarFile.getEntry(relativePath);
        if (entry != null && !entry.isDirectory()) {
            return jarFile.getInputStream(entry);
        }
        return null;
    }

    @Override
    public byte[] readFile(String relativePath) throws IOException {
        var entry = jarFile.getEntry(relativePath);
        if (entry != null && !entry.isDirectory()) {
            try (var input = jarFile.getInputStream(entry)) {
                // TODO in theory we can at least use entry uncompressed size as a hint here
                return input.readAllBytes();
            }
        }
        return null;
    }

    @Override
    public boolean hasContentRoot(Path path) {
        return this.path.equals(path);
    }

    @Override
    public Stream<URL> getClasspathRoots() {
        try {
            return Stream.of(this.path.toUri().toURL());
        } catch (MalformedURLException e) {
            LOG.error("Failed to convert path to URL: {}", path, e);
            return Stream.of();
        }
    }

    @Override
    public void visitContent(ModContentVisitor visitor) {
        var it = jarFile.entries().asIterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.isDirectory()) {
                continue;
            }
            var relativePath = PathNormalization.normalize(entry.getName());
            visitor.visit(relativePath, () -> jarFile.getInputStream(entry), () -> new ModContentAttributes(
                    entry.getLastModifiedTime(), entry.getSize()));
        }
    }

    @Nullable
    public String getMainJarManifestAttribute(Attributes.Name name) {
        var manifest = getJarManifest();
        if (manifest == null) {
            return null;
        }
        return manifest.getMainAttributes().getValue(name);
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        try {
            jarFile.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close ZIP-File " + path, e);
        }
    }

    public Optional<String> getCodeSigningFingerprint() {
        return Optional.empty(); // TODO
    }

    public Optional<String> getTrustData() {
        return Optional.empty(); // TODO
    }
}
