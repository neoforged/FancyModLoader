package net.neoforged.fml.classloading;

import net.neoforged.fml.jarcontents.JarContents;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.module.ModuleDescriptor;

/**
 * Links {@link JarContents} with how that content will be loaded as a JPMS module.
 */
public record JarContentsModule(JarContents contents, ModuleDescriptor moduleDescriptor) {
    @VisibleForTesting
    public JarContentsModule(JarContents contents) {
        this(contents, JarMetadata.from(contents).descriptor());
    }

    public String moduleName() {
        return moduleDescriptor.name();
    }
}
