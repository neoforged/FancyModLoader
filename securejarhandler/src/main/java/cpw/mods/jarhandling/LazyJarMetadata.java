package cpw.mods.jarhandling;

import org.jetbrains.annotations.Nullable;

import java.lang.module.ModuleDescriptor;

/**
 * Base class for {@link JarMetadata} implementations that lazily compute their descriptor.
 * This is recommended because descriptor computation can then run in parallel.
 */
public abstract class LazyJarMetadata implements JarMetadata {
    @Nullable
    private ModuleDescriptor descriptor;

    @Override
    public final ModuleDescriptor descriptor() {
        if (descriptor == null) {
            descriptor = computeDescriptor();
        }
        return descriptor;
    }

    /**
     * Computes the module descriptor for this jar.
     * This method is called at most once.
     */
    protected abstract ModuleDescriptor computeDescriptor();
}
