package cpw.mods.jarhandling;

import org.jetbrains.annotations.Nullable;

/**
 * Holder class for name and version of a module, used in {@link JarMetadata} computations.
 * Unfortunately, interfaces cannot hold private records (yet?).
 */
record NameAndVersion(String name, @Nullable String version) {}
