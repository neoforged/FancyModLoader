package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.util.LambdaExceptionUtils;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

public class Jar implements SecureJar {
    private final JarContents container;
    private final Manifest manifest;
    private final JarModuleDataProvider moduleDataProvider;
    private final JarMetadata metadata;

    @Nullable
    private UnionFileSystem filesystem;

    public static Jar of(Path path) throws IOException {
        return of(JarContents.ofPath(path));
    }

    public static Jar of(JarContents container) throws IOException {
        return of(container, JarMetadata.from(container));
    }

    public static Jar of(JarContents container, JarMetadata metadata) {
        return new Jar(container, metadata);
    }

    private Jar(JarContents container, JarMetadata metadata) {
        this.container = container;
        this.manifest = container.getJarManifest();

        this.moduleDataProvider = new JarModuleDataProvider(this);
        this.metadata = metadata;
    }

    @Override
    public JarContents container() {
        return container;
    }

    @Nullable
    public URI getURI() {
        var primaryPath = container.getPrimaryPath();
        if (primaryPath != null) {
            return primaryPath.toUri();
        }
        return null;
    }

    public ModuleDescriptor computeDescriptor() {
        return metadata.descriptor();
    }

    @Override
    public ModuleDataProvider moduleDataProvider() {
        return moduleDataProvider;
    }

    @Override
    public Path getPrimaryPath() {
        return container.getPrimaryPath();
    }

    public Optional<URI> findFile(final String name) {
        return container.findFile(name);
    }

    @Override
    public String name() {
        return metadata.name();
    }

    @Override
    public Path getPath(String first, String... rest) {
        return filesystem.getPath(first, rest);
    }

    @Override
    public Path getRootPath() {
        return filesystem.getPath("");
    }

    @Override
    public void close() throws IOException {
        container.close();
    }

    @Override
    public String toString() {
        return "Jar[" + getURI() + "]";
    }

    private record JarModuleDataProvider(Jar jar) implements ModuleDataProvider {
        @Override
        public String name() {
            return jar.name();
        }

        @Override
        public ModuleDescriptor descriptor() {
            return jar.computeDescriptor();
        }

        @Override
        public URI uri() {
            return jar.getURI();
        }

        @Override
        public Optional<URI> findFile(final String name) {
            return jar.findFile(name);
        }

        @Override
        public Optional<InputStream> open(final String name) {
            return jar.findFile(name).map(Paths::get).map(LambdaExceptionUtils.rethrowFunction(Files::newInputStream));
        }

        @Override
        public Manifest getManifest() {
            return jar.manifest;
        }
    }
}
