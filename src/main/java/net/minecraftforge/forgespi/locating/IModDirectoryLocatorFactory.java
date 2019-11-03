package net.minecraftforge.forgespi.locating;

import java.nio.file.Path;

/**
 * Functional interface for generating a custom {@link IModLocator} from a directory, with a specific name.
 *
 * FML provides this factory at {@link net.minecraftforge.forgespi.Environment.Keys#MODDIRECTORYFACTORY} during
 * locator construction.
 */
public interface IModDirectoryLocatorFactory {
    IModLocator build(Path directory, String name);
}
