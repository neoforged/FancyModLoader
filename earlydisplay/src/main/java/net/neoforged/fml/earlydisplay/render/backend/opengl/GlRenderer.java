/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.sdl.SDLVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlRenderer.class);

    public static void configureWindowHints() {
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_CORE);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_FRAMEBUFFER_SRGB_CAPABLE, 1);

        int contextFlags = SDLVideo.SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG;
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DEBUG_OPENGL)) {
            LOGGER.info("Requesting the creation of an OpenGL debug context");
            contextFlags |= SDLVideo.SDL_GL_CONTEXT_DEBUG_FLAG;
        }
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_FLAGS, contextFlags);
    }

    public static ELSRenderBackend setupBackend(long windowHandle) {
        long glContext = SDLVideo.SDL_GL_CreateContext(windowHandle);
        SDLVideo.SDL_GL_MakeCurrent(windowHandle, glContext);
        SDLVideo.SDL_GL_SetSwapInterval(1);
        GLCapabilities capabilities = GL.createCapabilities();
        GlDebug.setCapabilities(capabilities);
        LOGGER.info("GL info: {} GL version {}, {}", GL33C.glGetString(GL33C.GL_RENDERER), GL33C.glGetString(GL33C.GL_VERSION), GL33C.glGetString(GL33C.GL_VENDOR));
        GlState.readFromOpenGL();
        return new GlRenderBackend(windowHandle, glContext);
    }

    private GlRenderer() {}
}
