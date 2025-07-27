package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * {@link JarMetadata} implementation for a modular jar.
 * Reads the module descriptor from the jar.
 */
public class ModuleJarMetadata extends LazyJarMetadata {
    private final byte[] originalDescriptorBytes;
    private final ModuleDescriptor originalDescriptor;
    private final JarContents jar;

    public ModuleJarMetadata(URI uri, JarContents jar) {
        this.jar = jar;
        try {
            this.originalDescriptorBytes = Files.readAllBytes(Path.of(uri));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read module-info.class from " + uri, e);
        }
        this.originalDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(originalDescriptorBytes));
    }

    @Override
    protected ModuleDescriptor computeDescriptor() {
        var fullDescriptor = ModuleDescriptor.read(ByteBuffer.wrap(originalDescriptorBytes), () -> ModuleDescriptorFactory.scanModulePackages(jar));

        // There are two cases in which we have to build a new descriptor:
        // 1) The original one didn't have a list of package names
        // 2) The original one wasn't an open module, we want all modules to be open
        if (originalDescriptor.isOpen() && originalDescriptor.packages().equals(fullDescriptor.packages())) {
            return originalDescriptor;
        }

        // Make a new open module and copy everything over
        // Note how "open" packages are not copied, since open modules cannot declare them
        var builder = ModuleDescriptor.newOpenModule(fullDescriptor.name());
        fullDescriptor.rawVersion().ifPresent(builder::version);
        fullDescriptor.exports().forEach(builder::exports);
        fullDescriptor.provides().forEach(builder::provides);
        fullDescriptor.uses().forEach(builder::uses);
        fullDescriptor.requires().forEach(builder::requires);
        fullDescriptor.mainClass().ifPresent(builder::mainClass);
        builder.packages(fullDescriptor.packages());

        return builder.build();
    }

    @Override
    public String name() {
        return originalDescriptor.name();
    }

    @Override
    @Nullable
    public String version() {
        return originalDescriptor.rawVersion().orElse(null);
    }
}
