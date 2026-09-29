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
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDL_Event;

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
        ErrorDisplayWindow window = new ErrorDisplayWindow(backend, assetsDir, assetIndex, errors, modsFolder, logFile, crashReportFile);

        long nextFrameTime = 0;
        while (!window.isClosed()) {
            long nanoTime = System.nanoTime();
            var timeToNextFrame = nextFrameTime - nanoTime;
            if (timeToNextFrame <= 0) {
                window.render();
                nextFrameTime = nanoTime + MINFRAMETIME;
                try (SDL_Event event = SDL_Event.malloc()) {
                    while (SDLEvents.SDL_PollEvent(event)) {
                        window.handleEvent(event);
                    }
                }
            } else {
                SDLEvents.SDL_WaitEventTimeout(null, (int) (timeToNextFrame / (double) TimeUnit.SECONDS.toNanos(1)));
            }
        }
        window.close(true);
        SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_VIDEO);

        if (THROW_ON_EXIT) {
            throw new FatalLoadingError();
        } else {
            System.exit(1);
        }
    }

    public static class FatalLoadingError extends RuntimeException {}

    private ErrorDisplay() {}
}
