package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Manifest;

public class Jar implements SecureJar {
    private final JarContents contents;
    private final JarModuleDataProvider moduleDataProvider;

    private final JarMetadata metadata;

    public Jar(JarContents contents, JarMetadata metadata) {
        this.contents = contents;
        this.moduleDataProvider = new JarModuleDataProvider(this);
        this.metadata = metadata;
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
        return contents.getPrimaryPath();
    }

    @Override
    public JarContents contents() {
        return contents;
    }

    public Optional<URI> findFile(final String name) {
        return contents.findFile(name);
    }

    @Override
    public String name() {
        return metadata.name();
    }

    @Override
    public void close() throws IOException {
        contents.close();
    }

    @Override
    public String toString() {
        return "Jar[" + getPrimaryPath() + "]";
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
            return jar.getPrimaryPath().toUri();
        }

        @Override
        public Optional<URI> findFile(final String name) {
            return jar.findFile(name);
        }

        @Override
        public Optional<InputStream> open(final String name) {
            try {
                return Optional.ofNullable(jar.contents.openFile(name));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open " + name, e);
            }
        }

        @Override
        public Manifest getManifest() {
            return jar.contents.getManifest();
        }
    }
}
