package cpw.mods.jarhandling;

import java.lang.module.ModuleDescriptor;
import org.jetbrains.annotations.Nullable;

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
     */
    protected abstract ModuleDescriptor computeDescriptor();
}
