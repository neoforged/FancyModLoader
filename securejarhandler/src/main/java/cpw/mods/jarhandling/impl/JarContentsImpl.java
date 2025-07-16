package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarResource;
import cpw.mods.jarhandling.JarResourceAttributes;
import cpw.mods.jarhandling.JarResourceVisitor;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import cpw.mods.niofs.union.UnionPathFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

public class JarContentsImpl implements JarContents {
    private static final UnionFileSystemProvider UFSP = (UnionFileSystemProvider) FileSystemProvider.installedProviders()
            .stream()
            .filter(fsp -> fsp.getScheme().equals("union"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Couldn't find UnionFileSystemProvider"));

    final UnionFileSystem filesystem;
    // Code signing data
    final JarSigningData signingData = new JarSigningData();
    // Manifest of the jar
    private final Manifest manifest;
    // Name overrides, if the jar is a multi-release jar
    private final Map<Path, Integer> nameOverrides;

    public JarContentsImpl(Path[] paths, Supplier<Manifest> defaultManifest, @Nullable UnionPathFilter pathFilter) {
        var validPaths = Arrays.stream(paths).filter(Files::exists).toArray(Path[]::new);
        if (validPaths.length == 0)
            throw new UncheckedIOException(new IOException("Invalid paths argument, contained no existing paths: " + Arrays.toString(paths)));
        this.filesystem = UFSP.newFileSystem(pathFilter, validPaths);
        // Find the manifest, and read its signing data
        this.manifest = readManifestAndSigningData(defaultManifest, validPaths);
        // Read multi-release jar information
        this.nameOverrides = readMultiReleaseInfo();
    }

    private Manifest readManifestAndSigningData(Supplier<Manifest> defaultManifest, Path[] validPaths) {
        try {
            for (int x = validPaths.length - 1; x >= 0; x--) { // Walk backwards because this is what cpw wanted?
                var path = validPaths[x];
                if (Files.isDirectory(path)) {
                    // Just a directory: read the manifest file, but don't do any signature verification
                    var manfile = path.resolve(JarFile.MANIFEST_NAME);
                    if (Files.exists(manfile)) {
                        try (var is = Files.newInputStream(manfile)) {
                            return new Manifest(is);
                        }
                    }
                } else {
                    try (var jis = new JarInputStream(Files.newInputStream(path))) {
                        // Jar file: use the signature verification code
                        signingData.readJarSigningData(jis);

                        if (jis.getManifest() != null) {
                            return new Manifest(jis.getManifest());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return defaultManifest.get();
    }

    /**
     * Read multi-release information from the jar.
     * Example of a multi-release jar layout:
     *
     * <pre>
     * jar root
     *   - A.class
     *   - B.class
     *   - C.class
     *   - D.class
     *   - META-INF
     *      - versions
     *         - 9
     *            - A.class
     *            - B.class
     *         - 10
     *            - A.class
     * </pre>
     */
    private Map<Path, Integer> readMultiReleaseInfo() {
        // Must have the manifest entry
        boolean isMultiRelease = Boolean.parseBoolean(getManifest().getMainAttributes().getValue("Multi-Release"));
        if (!isMultiRelease) {
            return Map.of();
        }

        var vers = filesystem.getRoot().resolve("META-INF/versions");
        if (!Files.isDirectory(vers)) return Map.of();

        try (var walk = Files.walk(vers)) {
            Map<Path, Integer> pathToJavaVersion = new HashMap<>();
            walk
                    // Look for files, not directories
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(p -> {
                        int javaVersion = Integer.parseInt(p.getName(2).toString());
                        Path remainder = p.subpath(3, p.getNameCount());
                        if (javaVersion <= Runtime.version().feature()) {
                            // Associate path with the highest supported java version
                            pathToJavaVersion.merge(remainder, javaVersion, Integer::max);
                        }
                    });
            return pathToJavaVersion;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    @Override
    public Path getPrimaryPath() {
        return filesystem.getPrimaryPath();
    }

    private Path fromRelativePath(String name) {
        var rel = filesystem.getPath(name);
        if (this.nameOverrides.containsKey(rel)) {
            rel = this.filesystem.getPath("META-INF", "versions", this.nameOverrides.get(rel).toString()).resolve(rel);
        }
        return this.filesystem.getRoot().resolve(rel);
    }

    @Override
    public Optional<URI> findFile(String name) {
        var path = fromRelativePath(name);
        return Optional.of(path).filter(Files::exists).map(Path::toUri);
    }

    @Override
    public InputStream openFile(String name) throws IOException {
        var path = fromRelativePath(name);
        try {
            return Files.newInputStream(path);
        } catch (AccessDeniedException e) {
            if (Files.isDirectory(path)) {
                return null;
            }
            throw e;
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public void close() throws IOException {
        filesystem.close();
    }

    @Override
    public Collection<Path> getContentRoots() {
        return Collections.unmodifiableCollection(filesystem.getBasePaths());
    }

    @Override
    public @Nullable JarResource get(String relativePath) {
        var path = fromRelativePath(relativePath);
        if (Files.isRegularFile(path)) {
            return new JarResource() {
                @Override
                public InputStream open() throws IOException {
                    return Files.newInputStream(path);
                }

                @Override
                public JarResourceAttributes attributes() throws IOException {
                    return readAttributes(path);
                }

                @Override
                public JarResource retain() {
                    return this;
                }
            };
        }
        return null;
    }

    @Override
    public byte[] readFile(String relativePath) throws IOException {
        Path path = fromRelativePath(relativePath);
        try {
            return Files.readAllBytes(path);
        } catch (AccessDeniedException e) {
            if (Files.isDirectory(path)) {
                return null;
            }
            throw e;
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public void visitContent(String startingFolder, JarResourceVisitor visitor) {
        var startingPoint = getVisitStartingPoint(startingFolder);
        if (!startingPoint.startsWith(filesystem.getRoot())) {
            return; // Don't allow ../ escapes
        }
        if (!Files.isDirectory(startingPoint)) {
            return;
        }

        try (var stream = Files.walk(startingPoint)) {
            var locatedResource = new PathJarResource(null);
            stream.forEach(path -> {
                if (Files.isRegularFile(path)) {
                    var relativePath = PathNormalization.normalize(path.toString());
                    locatedResource.path = path;
                    visitor.visit(relativePath, locatedResource);
                }
            });
        } catch (NoSuchFileException ignored) {
            // The specific subfolder doesn't exist
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk contents of " + this, e);
        }
    }

    private Path getVisitStartingPoint(String startingFolder) {
        startingFolder = PathNormalization.normalize(startingFolder);

        var startingPoint = filesystem.getRoot();
        if (!startingFolder.isEmpty()) {
            startingPoint = startingPoint.resolve(startingFolder).normalize();
        }
        return startingPoint;
    }

    private static JarResourceAttributes readAttributes(Path path) throws IOException {
        var attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
        return new JarResourceAttributes(attributes.lastModifiedTime(), attributes.size());
    }

    @Override
    public String toString() {
        return getPrimaryPath().toString();
    }

    private static final class PathJarResource implements JarResource {
        private Path path;

        public PathJarResource(Path path) {
            this.path = path;
        }

        @Override
        public InputStream open() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public JarResourceAttributes attributes() throws IOException {
            var attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
            return new JarResourceAttributes(attributes.lastModifiedTime(), attributes.size());
        }

        @Override
        public JarResource retain() {
            return new PathJarResource(path);
        }
    }
}
