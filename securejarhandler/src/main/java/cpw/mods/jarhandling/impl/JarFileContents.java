package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarResource;
import cpw.mods.jarhandling.JarResourceAttributes;
import cpw.mods.jarhandling.JarResourceVisitor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JarFileContents implements JarContents {
    private final Path path;
    private final JarFile jarFile;
    private final Manifest jarManifest;
    private FileSystem zipFs;

    public JarFileContents(Path path) throws IOException {
        this.path = path;
        this.jarFile = new JarFile(path.toFile(), true, JarFile.OPEN_READ, JarFile.runtimeVersion());
        this.jarManifest = Objects.requireNonNullElse(this.jarFile.getManifest(), EmptyManifest.INSTANCE);
    }

    @Override
    public Optional<URI> findFile(String relativePath) {
        var entry = jarFile.getEntry(relativePath);
        if (entry != null && !entry.isDirectory()) {
            // getOrCreateZipFs(); // Must be created for URIs to map back to the Zip FS in NIO
            return Optional.of(URI.create("jar:" + path.toUri() + "!/" + relativePath));
        }
        return Optional.empty();
    }

    @Override
    public Path getPrimaryPath() {
        return path;
    }

    @Override
    public Collection<Path> getContentRoots() {
        return List.of(path);
    }

    @Override
    public Optional<String> getChecksum() {
        try (var in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream digestIn = new DigestInputStream(in, digest);
            digestIn.transferTo(OutputStream.nullOutputStream());
            return Optional.of(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compute checksum for " + path, e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Standard JCA algorithm is missing.", e);
        }
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Nullable
    @Override
    public Manifest getManifest() {
        return jarManifest;
    }

    @Override
    public JarResource get(String relativePath) {
        var entry = jarFile.getEntry(relativePath);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        return new JarResource() {
            @Override
            public InputStream open() throws IOException {
                return jarFile.getInputStream(entry);
            }

            @Override
            public JarResourceAttributes attributes() {
                return getModContentAttributes(entry);
            }

            @Override
            public JarResource retain() {
                return this;
            }
        };
    }

    @Override
    public boolean containsFile(String relativePath) {
        PathNormalization.assertNormalized(relativePath);
        return jarFile.getEntry(relativePath) != null;
    }

    @Override
    public InputStream openFile(String relativePath) throws IOException {
        PathNormalization.assertNormalized(relativePath);
        var entry = jarFile.getEntry(relativePath);
        if (entry != null && !entry.isDirectory()) {
            return jarFile.getInputStream(entry);
        }
        return null;
    }

    @Override
    public byte[] readFile(String relativePath) throws IOException {
        PathNormalization.assertNormalized(relativePath);
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
    public void visitContent(String startingFolder, JarResourceVisitor visitor) {
        startingFolder = PathNormalization.normalize(startingFolder);

        var resource = new JarEntryResource();
        var it = jarFile.entries().asIterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.isDirectory()) {
                continue;
            }

            if (!startingFolder.isEmpty() && !entry.getName().startsWith(startingFolder)) {
                continue;
            }

            var relativePath = PathNormalization.normalize(entry.getName());
            resource.entry = entry;
            visitor.visit(relativePath, resource);
        }
    }

    private static JarResourceAttributes getModContentAttributes(ZipEntry entry) {
        return new JarResourceAttributes(entry.getLastModifiedTime(), entry.getSize());
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

        try {
            if (zipFs != null) {
                zipFs.close();
                zipFs = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close ZIP-FileSystem " + zipFs, e);
        }
    }

    private synchronized FileSystem getOrCreateZipFs() {
        if (zipFs == null) {
            try {
                zipFs = FileSystems.newFileSystem(URI.create("jar:" + path.toUri()), Map.of());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open Zip FS on-demand for " + this, e);
            }
        }
        return zipFs;
    }

    private final class JarEntryResource implements JarResource {
        private JarEntry entry;

        @Override
        public InputStream open() throws IOException {
            return jarFile.getInputStream(entry);
        }

        @Override
        public JarResourceAttributes attributes() {
            return getModContentAttributes(entry);
        }

        @Override
        public JarResource retain() {
            var result = new JarEntryResource();
            result.entry = entry;
            return result;
        }
    }
}
