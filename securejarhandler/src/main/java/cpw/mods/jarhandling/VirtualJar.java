package cpw.mods.jarhandling;

import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.CodeSigner;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of {@link SecureJar} that does not actually contain any files,
 * but still defines packages.
 *
 * <p>This can be used by frameworks that generate classes at runtime, in specific packages,
 * and need to make a {@link SecureJar}-based module system implementation aware of these packages.
 */
public final class VirtualJar implements SecureJar {
    private final JarContents contents;

    /**
     * Creates a new virtual jar.
     *
     * @param name          the name of the virtual jar; will be used as the module name
     * @param referencePath a path to an existing directory or jar file, for debugging and display purposes
     *                      (for example a path to the real jar of the caller)
     * @param packages      the list of packages in this virtual jar
     */
    public VirtualJar(String name, Path referencePath, String... packages) {
        if (!Files.exists(referencePath)) {
            throw new IllegalArgumentException("VirtualJar reference path " + referencePath + " must exist");
        }

        this.moduleDescriptor = ModuleDescriptor.newAutomaticModule(name)
                .packages(Set.of(packages))
                .build();
        // Create a dummy file system from the reference path, with a filter that always returns false
        this.dummyFileSystem = UFSP.newFileSystem((path, basePath) -> false, referencePath);
        this.contents = JarContents.of(dummyFileSystem.getRoot());
    }

    // Implementation details below
    private static final UnionFileSystemProvider UFSP = (UnionFileSystemProvider) FileSystemProvider.installedProviders()
            .stream()
            .filter(fsp -> fsp.getScheme().equals("union"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Couldn't find UnionFileSystemProvider"));

    private final ModuleDescriptor moduleDescriptor;
    private final ModuleDataProvider moduleData = new VirtualJarModuleDataProvider();
    private final UnionFileSystem dummyFileSystem;
    private final Manifest manifest = new Manifest();

    @Override
    public ModuleDataProvider moduleDataProvider() {
        return moduleData;
    }

    @Override
    public Path getPrimaryPath() {
        return dummyFileSystem.getPrimaryPath();
    }

    @Override
    public JarContents contents() {
        return contents;
    }

    @Override
    public @Nullable CodeSigner[] getManifestSigners() {
        return null;
    }

    @Override
    public Status verifyPath(Path path) {
        return Status.NONE;
    }

    @Override
    public Status getFileStatus(String name) {
        return Status.NONE;
    }

    @Override
    public @Nullable Attributes getTrustedManifestEntries(String name) {
        return null;
    }

    @Override
    public boolean hasSecurityData() {
        return false;
    }

    @Override
    public String name() {
        return moduleDescriptor.name();
    }

    @Override
    public Path getPath(String first, String... rest) {
        return dummyFileSystem.getPath(first, rest);
    }

    @Override
    public Path getRootPath() {
        return dummyFileSystem.getRoot();
    }

    @Override
    public void close() throws IOException {
        dummyFileSystem.close();
    }

    private class VirtualJarModuleDataProvider implements ModuleDataProvider {
        @Override
        public String name() {
            return VirtualJar.this.name();
        }

        @Override
        public ModuleDescriptor descriptor() {
            return moduleDescriptor;
        }

        @Override
        @Nullable
        public URI uri() {
            return null;
        }

        @Override
        public Optional<URI> findFile(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(String name) {
            return Optional.empty();
        }

        @Override
        public Manifest getManifest() {
            return manifest;
        }

        @Override
        @Nullable
        public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) {
            return null;
        }
    }
}
