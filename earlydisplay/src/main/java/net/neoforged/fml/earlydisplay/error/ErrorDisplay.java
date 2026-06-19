/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.error;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Callback;

public final class ErrorDisplay {
    private static final boolean THROW_ON_EXIT = Boolean.getBoolean("fml.loadingErrorThrowOnExit");

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window

    public static void fatal(
            ELSRenderBackend backend,
            @Nullable String assetsDir,
            @Nullable String assetIndex,
            List<ModLoadingIssue> errors,
            @Nullable Path modsFolder,
            @Nullable Path logFile,
            @Nullable Path crashReportFile) {
        // Pre-clear all callbacks that may be left-over from the previous owner of the window
        long windowHandle = backend.getWindowHandle();
        clearCallbacks(windowHandle);

        ErrorDisplayWindow window = new ErrorDisplayWindow(backend, assetsDir, assetIndex, errors, modsFolder, logFile, crashReportFile);

        discard(GLFW.glfwSetWindowCloseCallback(windowHandle, window::handleClose));
        discard(GLFW.glfwSetCursorPosCallback(windowHandle, window::handleCursorPos));
        discard(GLFW.glfwSetScrollCallback(windowHandle, window::handleMouseScroll));
        discard(GLFW.glfwSetMouseButtonCallback(windowHandle, window::handleMouseButton));
        discard(GLFW.glfwSetKeyCallback(windowHandle, window::handleKey));

        long nextFrameTime = 0;
        while (!window.isClosed()) {
            long nanoTime = System.nanoTime();
            var timeToNextFrame = nextFrameTime - nanoTime;
            if (timeToNextFrame <= 0) {
                window.render();
                nextFrameTime = nanoTime + MINFRAMETIME;
                GLFW.glfwPollEvents();
            } else {
                GLFW.glfwWaitEventsTimeout(timeToNextFrame / (double) TimeUnit.SECONDS.toNanos(1));
            }
        }
        window.close(true);

        if (THROW_ON_EXIT) {
            throw new FatalLoadingError();
        } else {
            System.exit(1);
        }
    }

    private static void clearCallbacks(long windowHandle) {
        // Clear all other callbacks
        discard(GLFW.glfwSetWindowPosCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowSizeCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowCloseCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowRefreshCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowFocusCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowIconifyCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowMaximizeCallback(windowHandle, null));
        discard(GLFW.glfwSetFramebufferSizeCallback(windowHandle, null));
        discard(GLFW.glfwSetWindowContentScaleCallback(windowHandle, null));
        discard(GLFW.glfwSetKeyCallback(windowHandle, null));
        discard(GLFW.glfwSetCharCallback(windowHandle, null));
        discard(GLFW.glfwSetCharModsCallback(windowHandle, null));
        discard(GLFW.glfwSetMouseButtonCallback(windowHandle, null));
        discard(GLFW.glfwSetCursorPosCallback(windowHandle, null));
        discard(GLFW.glfwSetCursorEnterCallback(windowHandle, null));
        discard(GLFW.glfwSetScrollCallback(windowHandle, null));
        discard(GLFW.glfwSetDropCallback(windowHandle, null));
    }

    private static void discard(@Nullable Callback callback) {
        if (callback != null) {
            callback.close();
        }
    }

    public static class FatalLoadingError extends RuntimeException {}

    private ErrorDisplay() {}
}
