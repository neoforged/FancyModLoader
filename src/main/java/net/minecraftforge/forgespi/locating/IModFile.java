package net.minecraftforge.forgespi.locating;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.forgespi.language.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Represents a single "mod" file in the runtime.
 *
 * Although these are known as "Mod"-Files, they do not always represent mods.
 * However, they should be more treated as an extension or modification of minecraft.
 * And as such can contain any number of things, like language loaders, dependencies of other mods
 * or code which does not interact with minecraft at all and is just a utility for any of the other mod
 * files.
 */
public interface IModFile {
    /**
     * The language loaders which are included in this mod file.
     *
     * If this method returns any entries then {@link #getType()} has to return {@link Type#LIBRARY},
     * else this mod file will not be loaded in the proper module layer in 1.17 and above.
     *
     * As such, returning entries from this method is mutually exclusive with returning entries from {@link #getModInfos()}.
     *
     * @return The mod language providers provided by this mod file. (Also known as the loaders).
     */
    List<IModLanguageProvider> getLoaders();

    /**
     * Invoked to find a particular resource in this mod file, with the given path.
     *
     * @param pathName The string representation of the path to find the mod resource on.
     * @return The {@link Path} that represents the requested resource.
     */
    Path findResource(String... pathName);

    /**
     * The mod files specific string data substitution map.
     * The map returned here is used to interpolate values in the metadata of the included mods.
     * Examples of where this is used in FML: While parsing the mods.toml file, keys like:
     * ${file.xxxxx} are replaced with the values of this map, with keys xxxxx.
     *
     * @return The string substitution map used during metadata load.
     */
    Supplier<Map<String,Object>> getSubstitutionMap();

    /**
     * The type of the mod jar.
     * This primarily defines how and where this mod file is loaded into the module system.
     *
     * @return The type of the mod file.
     */
    Type getType();

    /**
     * The path to the underlying mod file.
     * @return The path to the mod file.
     */
    Path getFilePath();

    /**
     * The secure jar that represents this mod file.
     *
     * @return The secure jar.
     */
    SecureJar getSecureJar();

    /**
     * Sets the security status after verification of the mod file has been concluded.
     * The security status is only determined if the jar is to be loaded into the runtime.
     *
     * @param status The new status.
     */
    void setSecurityStatus(SecureJar.Status status);

    /**
     * Returns a list of all mods located inside this jar.
     *
     * If this method returns any entries then {@link #getType()} has to return {@link Type#MOD},
     * else this mod file will not be loaded in the proper module layer in 1.17 and above.
     *
     * As such returning entries from this method is mutually exclusive with {@link #getLoaders()}.
     *
     * @return The mods in this mod file.
     */
    List<IModInfo> getModInfos();

    /**
     * The metadata scan result of all the classes located inside this file.
     *
     * @return The metadata scan result.
     */
    ModFileScanData getScanResult();

    /**
     * The raw file name of this file.
     * @return The raw file name.
     */
    String getFileName();

    /**
     * The provider who provided the runtime with this jar.
     * Implicitly indicates what caused the load of the PR. (Mod in mods directory, mod in dev environment, etc)
     *
     * @return The provider of this file.
     */
    IModProvider getProvider();

    /**
     * The metadata info related to this particular file.
     * @return The info for this file.
     */
    IModFileInfo getModFileInfo();

    /**
     * The type of file.
     * Determines into which module layer the data is loaded and how metadata is loaded and processed.
     */
    enum Type {
        /**
         * A mod file holds mod code and loads in the game module layer
         */
        MOD,
        /**
         * A library can reference lang providers in the plugin module layer
         */
        LIBRARY,
        /**
         * A language provider provides a custom way to load mods in the plugin module layer
         */
        LANGPROVIDER,
        /**
         * A game library can reference MC code and loads in the game module layer
         */
        GAMELIBRARY
    }
}
