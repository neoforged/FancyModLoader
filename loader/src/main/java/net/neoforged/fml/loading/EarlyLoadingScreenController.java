/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

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
     * @return the state of the ELS window to be applied to the vanilla window
     */
    WindowState handOverToMinecraft(Supplier<Object> renderBackend);

    /**
     * After calling {@linkplain #handOverToMinecraft(Supplier) taking over} the main window, the game may still want to
     * periodically ask the loading screen to update itself independently. It will call this method to do so.
     */
    void periodicTick();

    /**
     * Sets a label for the main progress bar on the early loading screen.
     */
    void updateProgress(String label);

    /**
     * Clears all current progress in preparation for drawing a Minecraft overlay on top of the loading
     * screen.
     */
    void completeProgress();

    record WindowState(int x, int y, int width, int height, boolean minimized, boolean maximized) {}
}
