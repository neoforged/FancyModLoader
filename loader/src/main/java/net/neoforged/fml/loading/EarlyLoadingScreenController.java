package net.neoforged.fml.loading;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Interface for use by NeoForge to control the early loading screen.
 */
public interface EarlyLoadingScreenController {

    /**
     * Gets the current loading screen controller.
     */
    @Nullable
    static EarlyLoadingScreenController current() {
        return ImmediateWindowHandler.provider;
    }

    /**
     * Takes over ownership of the GLFW window created by the early loading screen.
     * <p>
     * This method can only be called once and once this method is called, any off-thread
     * interaction with the window seizes.
     *
     * @return The GLFW window handle for the window in a state that can be used by the game.
     */
    long takeOverGlfwWindow();

    /**
     * Return a Supplier of an object extending the LoadingOverlay class from Mojang. This is what will be used once
     * the Mojang window code has taken over rendering of the window, to render the later stages of the loading process.
     *
     * @param mc   This supplies the Minecraft object
     * @param ri   This supplies the ReloadInstance object that tells us when the loading is finished
     * @param ex   This Consumes the final state of the loading - if it's an error you pass it the Throwable, otherwise you
     *             pass Optional.empty()
     * @param fade This is the fade flag passed to LoadingOverlay. You probably want to ignore it.
     * @param <T>  This is the type LoadingOverlay to allow type binding on the Mojang side
     * @return A supplier of your later LoadingOverlay screen.
     */
    <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade);

}
