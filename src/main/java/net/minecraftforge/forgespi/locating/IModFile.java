package net.minecraftforge.forgespi.locating;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.forgespi.language.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public interface IModFile {
    List<IModLanguageProvider> getLoaders();

    Path findResource(String... pathName);

    Supplier<Map<String,Object>> getSubstitutionMap();

    Type getType();

    Path getFilePath();

    SecureJar getSecureJar();

    void setSecurityStatus(SecureJar.Status status);

    List<IModInfo> getModInfos();

    ModFileScanData getScanResult();

    String getFileName();

    IModLocator getLocator();

    IModFileInfo getModFileInfo();

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
