package net.minecraftforge.forgespi.locating;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.language.IModFileInfo;

/**
 * A factory to build new mod file instances.
 */
public interface ModFileFactory {
    /**
     * The current instance. Equals to {@link Environment#getModFileFactory()}, of the current environment instance.
     */
    ModFileFactory FACTORY = Environment.get().getModFileFactory();

    /**
     * Builds a new mod file instance depending on the current runtime.
     * @param jar The secure jar to load the mod file from.
     * @param provider The provider which is offering the mod file for loading-
     * @param parser The parser which is responsible for parsing the metadata of the file itself.
     * @return The mod file.
     */
    IModFile build(final SecureJar jar, final IModProvider provider, ModFileInfoParser parser);

    /**
     * A parser specification for building a particular mod files metadata.
     */
    interface ModFileInfoParser {
        /**
         * Invoked to get the freshly build mod files metadata.
         *
         * @param file The file to parse the metadata for.
         * @return The mod file metadata info.
         */
        IModFileInfo build(IModFile file);
    }
}
