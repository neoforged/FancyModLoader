package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.LazyJarMetadata;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * {@link JarMetadata} implementation for a modular jar.
 * Reads the module descriptor from the jar.
 */
public class ModuleJarMetadata extends LazyJarMetadata {
    private final ModuleDescriptor originalDescriptor;
    @Nullable
    private final Supplier<Set<String>> packagesSupplier;

    public ModuleJarMetadata(URI uri, Supplier<Set<String>> packagesSupplier) {
        boolean[] packagesSupplierUsed = { false };
        try (var is = new BufferedInputStream(Files.newInputStream(Path.of(uri)))) {
            this.originalDescriptor = ModuleDescriptor.read(is, () -> {
                packagesSupplierUsed[0] = true;
                return Set.of();
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (packagesSupplierUsed[0]) {
            this.packagesSupplier = Objects.requireNonNull(packagesSupplier, "packagesSupplier");
        } else {
            this.packagesSupplier = null;
        }
    }

    @Override
    protected ModuleDescriptor computeDescriptor() {
        // There are two cases in which we have to build a new descriptor:
        // 1) The original one didn't have a list of package names
        // 2) The original one wasn't an open module, we want all modules to be open
        if (originalDescriptor.isOpen() && packagesSupplier == null) {
            return originalDescriptor;
        }

        // Make a new open module and copy everything over
        // Note how "open" packages are not copied, since open modules cannot declare them
        var builder = ModuleDescriptor.newOpenModule(originalDescriptor.name());
        originalDescriptor.rawVersion().ifPresent(builder::version);
        originalDescriptor.exports().forEach(builder::exports);
        originalDescriptor.provides().forEach(builder::provides);
        originalDescriptor.uses().forEach(builder::uses);
        originalDescriptor.requires().forEach(builder::requires);
        originalDescriptor.mainClass().ifPresent(builder::mainClass);
        if (packagesSupplier != null) {
            builder.packages(packagesSupplier.get());
        } else {
            builder.packages(originalDescriptor.packages());
        }

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
