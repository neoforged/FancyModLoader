package net.minecraftforge.forgespi.locating;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Describes objects which can provide mods (or related jars) to the loading runtime.
 */
public interface IModProvider
{
    /**
     * The name of the provider.
     * Has to be unique between all providers loaded into the runtime.
     *
     * @return The name.
     */
    String name();

    /**
     * Invoked to scan a particular {@link IModFile} for metadata.
     *
     * @param modFile The mod file to scan.
     * @param pathConsumer A consumer which extracts metadata from the path given.
     */
    void scanFile(IModFile modFile, Consumer<Path> pathConsumer);

    /**
     * Invoked with the game startup arguments to allow for configuration of the provider.
     *
     * @param arguments The arguments.
     */
    void initArguments(Map<String, ?> arguments);

    /**
     * Indicates if the given mod file is valid.
     *
     * @param modFile The mod file in question.
     * @return True to mark as valid, false otherwise.
     */
    boolean isValid(IModFile modFile);
}
