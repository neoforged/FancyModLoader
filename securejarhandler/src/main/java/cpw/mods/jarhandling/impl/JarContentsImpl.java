package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import cpw.mods.niofs.union.UnionPathFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
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

    @Override
    public Optional<URI> findFile(String name) {
        var rel = filesystem.getPath(name);
        if (this.nameOverrides.containsKey(rel)) {
            rel = this.filesystem.getPath("META-INF", "versions", this.nameOverrides.get(rel).toString()).resolve(rel);
        }
        return Optional.of(this.filesystem.getRoot().resolve(rel)).filter(Files::exists).map(Path::toUri);
    }

    @Override
    public InputStream getResourceAsStream(String name) throws IOException {
        var rel = filesystem.getPath(name);
        if (this.nameOverrides.containsKey(rel)) {
            rel = this.filesystem.getPath("META-INF", "versions", this.nameOverrides.get(rel).toString()).resolve(rel);
        }
        var path = this.filesystem.getRoot().resolve(rel);
        try {
            return Files.newInputStream(path);
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
    public String toString() {
        return getPrimaryPath().toString();
    }
}
