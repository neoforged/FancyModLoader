package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class ModuleJarMetadata implements JarMetadata {
    private final ModuleDescriptor descriptor;

    public ModuleJarMetadata(final URI uri, final Set<String> packages) {
        try {
            this.descriptor = ModuleDescriptor.read(Files.newInputStream(Path.of(uri)), ()->packages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String name() {
        return descriptor.name();
    }

    @Override
    public String version() {
        return descriptor.version().toString();
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }
}
