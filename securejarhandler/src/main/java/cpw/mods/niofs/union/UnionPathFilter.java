package cpw.mods.niofs.union;

import java.nio.file.Path;

/**
 * Filter for paths in a {@link UnionFileSystem}.
 */
@FunctionalInterface
public interface UnionPathFilter {
    /**
     * Test if an entry should be included in the union filesystem.
     *
     * @param entry    the path of the entry being checked, relative to the base path
     * @param basePath the base path, i.e. one of the root paths the filesystem is built out of
     * @return {@code true} to include the entry, {@code} false to exclude it
     */
    boolean test(String entry, Path basePath);
}
