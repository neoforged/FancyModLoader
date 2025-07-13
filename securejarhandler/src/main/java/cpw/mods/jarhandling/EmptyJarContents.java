package cpw.mods.jarhandling;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

public final class EmptyJarContents implements JarContents {
    private final Path path;

    public EmptyJarContents(Path path) {
        this.path = path;
    }

    @Override
    public @Nullable String getManifestAttribute(String name) {
        return null;
    }

    @Override
    public @Nullable String getManifestAttribute(Attributes.Name name) {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return new Manifest();
    }

    @Override
    public Optional<URI> findFile(String relativePath) {
        return Optional.empty();
    }

    @Override
    public @Nullable JarResource get(String relativePath) {
        return null;
    }

    @Override
    public boolean containsFile(String relativePath) {
        return false;
    }

    @Override
    public Path getPrimaryPath() {
        return path;
    }

    @Override
    public @Nullable InputStream openFile(String relativePath) {
        return null;
    }

    @Override
    public byte @Nullable [] readFile(String relativePath) {
        return null;
    }

    @Override
    public boolean hasContentRoot(Path path) {
        return false;
    }

    @Override
    public void visitContent(String startingFolder, JarResourceVisitor visitor) {}

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "empty(" + path + ")";
    }
}
