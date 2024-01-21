package net.neoforged.neoforgespi.coremod;

import cpw.mods.modlauncher.api.ITransformer;

/**
 * Provide using the Java {@link java.util.ServiceLoader} mechanism.
 */
public interface ICoreMod {
    Iterable<? extends ITransformer<?>> getTransformers();
}
