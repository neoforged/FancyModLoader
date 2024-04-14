package net.neoforged.fml;

import java.util.ServiceLoader;

/**
 * Callback invoked after {@link ModContainer}s are created and the {@link ModList} is populated,
 * but before mods are constructed.
 *
 * <p>Instances are loaded via {@link ServiceLoader} from GAME and upper layers.
 */
@FunctionalInterface
public interface IModListReadyCallback {
    void onModListReady(ModList modList);
}
