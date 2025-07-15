/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.error;

import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.render.GlState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Callback;

public final class ErrorDisplay {
    private static final boolean THROW_ON_EXIT = Boolean.getBoolean("fml.loadingErrorThrowOnExit");

    public static void fatal(long windowHandle, List<ModLoadingIssue> errors, Path modsFolder, Path logFile, Path crashReportFile) {
        GLFW.glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();

        ErrorDisplayWindow window = new ErrorDisplayWindow(windowHandle, errors, modsFolder, logFile, crashReportFile);

        discard(GLFW.glfwSetWindowCloseCallback(window.windowHandle, window::handleClose));
        discard(GLFW.glfwSetCursorPosCallback(window.windowHandle, window::handleCursorPos));
        discard(GLFW.glfwSetScrollCallback(window.windowHandle, window::handleMouseScroll));
        discard(GLFW.glfwSetMouseButtonCallback(window.windowHandle, window::handleMouseButton));
        discard(GLFW.glfwSetKeyCallback(window.windowHandle, window::handleKey));

        GlState.readFromOpenGL();
        while (!window.isClosed()) {
            window.render();
            GLFW.glfwPollEvents();
        }
        window.teardown();

        if (THROW_ON_EXIT) {
            throw new FatalLoadingError();
        } else {
            System.exit(1);
        }
    }

    private static void discard(@Nullable Callback callback) {
        if (callback != null) {
            callback.close();
        }
    }

    public static class FatalLoadingError extends RuntimeException {}

    private ErrorDisplay() {}
}
