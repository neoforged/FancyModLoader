/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
/*
package net.neoforged.fml.earlydisplay.render;

import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.opengl.GlRenderer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;

public class WithOffScreenGLSurface implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithOffScreenGLSurface.class);
    private static final String KEY_BACKEND = "backend";

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            throw new IllegalStateException("Unable to initialize SDL");
        }

        var store = extensionContext.getStore(NAMESPACE);

        GlRenderer.configureWindowHints();

        long windowId = SDLVideo.SDL_CreateWindow("Offscreen Test Window", 800, 600, SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY);
        if (windowId == 0L) {
            throw new IllegalStateException("Failed to create window: " + SDLError.SDL_GetError());
        }

        ELSRenderBackend backend = GlRenderer.setupBackend(windowId);
        store.put(KEY_BACKEND, backend);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        ELSRenderBackend backend = (ELSRenderBackend) extensionContext.getStore(NAMESPACE).get(KEY_BACKEND);
        backend.close();
        SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_VIDEO);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        ELSRenderBackend backend = (ELSRenderBackend) context.getStore(NAMESPACE).get(KEY_BACKEND);
        backend.acquireContextOwnership(false);
        SDLEvents.SDL_PumpEvents();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ELSRenderBackend backend = (ELSRenderBackend) context.getStore(NAMESPACE).get(KEY_BACKEND);
        backend.releaseContextOwnership();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == ELSRenderBackend.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(NAMESPACE).get(KEY_BACKEND);
    }
}
*/