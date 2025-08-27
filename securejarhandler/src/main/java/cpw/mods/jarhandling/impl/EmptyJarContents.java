package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarResource;
import cpw.mods.jarhandling.JarResourceVisitor;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

public final class EmptyJarContents implements JarContents {
    private final Path path;

    public EmptyJarContents(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override
    public Optional<String> getChecksum() {
        return Optional.empty();
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
    public Collection<Path> getContentRoots() {
        return List.of();
    }

    @Override
    public Manifest getManifest() {
        return EmptyManifest.INSTANCE;
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
    public void visitContent(String startingFolder, JarResourceVisitor visitor) {}

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "empty(" + path + ")";
    }
}
