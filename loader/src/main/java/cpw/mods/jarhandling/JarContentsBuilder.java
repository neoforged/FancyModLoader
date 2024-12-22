package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.JarContentsImpl;
import cpw.mods.niofs.union.UnionPathFilter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import org.jetbrains.annotations.Nullable;

/**
 * Builder for {@link JarContents}.
 */
public final class JarContentsBuilder {
    private Path[] paths = new Path[0];
    private Supplier<Manifest> defaultManifest = Manifest::new;
    @Nullable
    private UnionPathFilter pathFilter = null;

    public JarContentsBuilder() {}

    /**
     * Sets the root paths for the files of this jar.
     */
    public JarContentsBuilder paths(Path... paths) {
        this.paths = paths;
        return this;
    }

    /**
     * Overrides the default manifest for this jar.
     * The default manifest is only used when the jar does not provide a manifest already.
     */
    public JarContentsBuilder defaultManifest(Supplier<Manifest> manifest) {
        Objects.requireNonNull(manifest);

        this.defaultManifest = manifest;
        return this;
    }

    /**
     * Overrides the path filter for this jar, to exclude some entries from the underlying file system.
     *
     * @see UnionPathFilter
     */
    public JarContentsBuilder pathFilter(@Nullable UnionPathFilter pathFilter) {
        this.pathFilter = pathFilter;
        return this;
    }

    /**
     * Builds the jar.
     */
    public JarContents build() {
        return new JarContentsImpl(paths, defaultManifest, pathFilter == null ? null : pathFilter::test);
    }
}
