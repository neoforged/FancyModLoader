package net.minecraftforge.forgespi.coremod;

import cpw.mods.modlauncher.api.*;

import java.util.*;

/**
 * Core Mod Provider - core mod logic is implemented
 * in a separate library. Connection is provided here
 *
 */
public interface ICoreModProvider {
    /**
     * Add a coremod file to the list of coremods
     * @param file the file to add
     */
    void addCoreMod(ICoreModFile file);

    /**
     * Return the completed list of transformers for all coremods
     * @return all coremod transformers
     */
    List<ITransformer<?>> getCoreModTransformers();
}
