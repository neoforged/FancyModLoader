/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.io.Closeable;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for use by NeoForge to control the early loading screen.
 */
public interface EarlyLoadingScreenController extends Closeable {
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

    @Override
    default void close() {}

    /**
     * A platform-independent snapshot of the early window's presentation state.
     *
     * @param x          the horizontal screen coordinate of the window
     * @param y          the vertical screen coordinate of the window
     * @param width      the width of the window's content area
     * @param height     the height of the window's content area
     * @param posValid   whether {@code x} and {@code y} contain a valid window position
     * @param minimized  whether the window is minimized or iconified
     * @param maximized  whether the window is in the platform's maximized or zoomed state
     * @param fullscreen whether the window is in a platform fullscreen mode, distinct from maximization
     */
    record WindowState(
            int x,
            int y,
            int width,
            int height,
            boolean posValid,
            boolean minimized,
            boolean maximized,
            boolean fullscreen) {}
}
