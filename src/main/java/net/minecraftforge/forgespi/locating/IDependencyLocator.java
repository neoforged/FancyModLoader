package net.minecraftforge.forgespi.locating;

import java.util.List;

/**
 * Loaded as a ServiceLoader. Takes mechanisms for locating candidate "mod-dependencies".
 * and transforms them into {@link IModFile} objects.
 */
public interface IDependencyLocator extends IModProvider
{
    /**
     * Invoked to find all mod dependencies that this dependency locator can find.
     * It is not guaranteed that all these are loaded into the runtime,
     * as such the result of this method should be seen as a list of candidates to load.
     *
     * @return All found, or discovered, mod files which function as dependencies.
     */
    List<IModFile> scanMods(final Iterable<IModFile> loadedMods);
}
