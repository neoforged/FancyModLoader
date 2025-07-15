/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL32C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL32C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.GL_NEAREST;
import static org.lwjgl.opengl.GL32C.GL_RGBA;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL32C.glClear;
import static org.lwjgl.opengl.GL32C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL32C.glDeleteTextures;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL32C.glGenFramebuffers;
import static org.lwjgl.opengl.GL32C.glGenTextures;
import static org.lwjgl.opengl.GL32C.glTexImage2D;
import static org.lwjgl.opengl.GL32C.glTexParameteri;

import java.nio.IntBuffer;
import net.neoforged.fml.earlydisplay.render.elements.RenderElement;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;

public class EarlyFramebuffer {
    private final int framebuffer;
    private final int texture;
    private int width;
    private int height;

    public EarlyFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.framebuffer = glGenFramebuffers();
        this.texture = glGenTextures();
        GlState.bindFramebuffer(this.framebuffer);
        GlDebug.labelFramebuffer(this.framebuffer, "EarlyDisplay framebuffer");

        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(this.texture);
        GlDebug.labelTexture(this.texture, "EarlyDisplay backbuffer");
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.texture, 0);
        GlState.bindFramebuffer(0);
    }

    public void activate() {
        GlState.bindFramebuffer(this.framebuffer);
    }

    public void deactivate() {
        GlState.bindFramebuffer(0);
    }

    public void blitToScreen(ThemeColor backgroundColor, int windowFBWidth, int windowFBHeight) {
        var wscale = ((float) windowFBWidth / width);
        var hscale = ((float) windowFBHeight / height);
        var scale = Math.min(wscale, hscale) / 2f;
        var wleft = (int) (windowFBWidth * 0.5f - scale * width);
        var wtop = (int) (windowFBHeight * 0.5f - scale * height);
        var wright = (int) (windowFBWidth * 0.5f + scale * width);
        var wbottom = (int) (windowFBHeight * 0.5f + scale * height);
        GlState.bindDrawFramebuffer(0);
        GlState.bindReadFramebuffer(this.framebuffer);
        GlState.clearColor(backgroundColor.r(), backgroundColor.g(), backgroundColor.b(), 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        // src Y are flipped, since our FB is flipped
        glBlitFramebuffer(
                0,
                height,
                width,
                0,
                RenderElement.clamp(wleft, 0, windowFBWidth),
                RenderElement.clamp(wtop, 0, windowFBHeight),
                RenderElement.clamp(wright, 0, windowFBWidth),
                RenderElement.clamp(wbottom, 0, windowFBHeight),
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST);
        GlState.bindFramebuffer(0);
    }

    int getTexture() {
        return this.texture;
    }

    public void resize(int width, int height) {
        if (this.width != width || this.height != height) {
            GlState.bindFramebuffer(framebuffer);
            GlState.bindTexture2D(texture);
            this.width = width;
            this.height = height;
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer) null);
        }
    }

    public void close() {
        glDeleteTextures(this.texture);
        glDeleteFramebuffers(this.framebuffer);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
