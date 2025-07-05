package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * {@link JarMetadata} implementation for a modular jar.
 * Reads the module descriptor from the jar.
 */
public class ModuleJarMetadata implements JarMetadata {
    private final ModuleDescriptor descriptor;

    public ModuleJarMetadata(URI uri) {
        ModuleDescriptor descriptor;
        try (var is = new BufferedInputStream(Files.newInputStream(Path.of(uri)))) {
            descriptor = ModuleDescriptor.read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // We convert all modules to open modules automatically
        if (!descriptor.isOpen()) {
            // Make a new open module and copy everything over
            // Note how "open" packages are not copied, since open modules cannot declare them
            var builder = ModuleDescriptor.newOpenModule(descriptor.name());
            descriptor.rawVersion().ifPresent(builder::version);
            descriptor.exports().forEach(builder::exports);
            descriptor.provides().forEach(builder::provides);
            descriptor.uses().forEach(builder::uses);
            descriptor.requires().forEach(builder::requires);
            descriptor.mainClass().ifPresent(builder::mainClass);
            builder.packages(descriptor.packages());

            descriptor = builder.build();
        }

        this.descriptor = descriptor;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String name() {
        return descriptor.name();
    }

    @Override
    @Nullable
    public String version() {
        return descriptor.rawVersion().orElse(null);
    }
}
