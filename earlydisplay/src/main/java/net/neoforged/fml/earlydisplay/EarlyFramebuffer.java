/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.IntBuffer;

public class EarlyFramebuffer {
    private final int framebuffer;
    private final int texture;

    private final RenderElement.DisplayContext context;

    EarlyFramebuffer(final RenderElement.DisplayContext context) {
        this.context = context;
        this.framebuffer = glGenFramebuffers();
        this.texture = glGenTextures();
        GlState.bindFramebuffer(this.framebuffer);
        GlDebug.labelFramebuffer(this.framebuffer, "EarlyDisplay framebuffer");

        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(this.texture);
        GlDebug.labelTexture(this.texture, "EarlyDisplay backbuffer");
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, context.width() * context.scale(), context.height() * context.scale(), 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer) null);
        glTexParameterIi(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameterIi(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.texture, 0);
        GlState.bindFramebuffer(0);
    }

    void activate() {
        GlState.bindFramebuffer(this.framebuffer);
    }

    void deactivate() {
        GlState.bindFramebuffer(0);
    }

    void draw(int windowFBWidth, int windowFBHeight) {
        var wscale = ((float) windowFBWidth / this.context.width());
        var hscale = ((float) windowFBHeight / this.context.height());
        var scale = this.context.scale() * Math.min(wscale, hscale) / 2f;
        var wleft = (int) (windowFBWidth * 0.5f - scale * this.context.width());
        var wtop = (int) (windowFBHeight * 0.5f - scale * this.context.height());
        var wright = (int) (windowFBWidth * 0.5f + scale * this.context.width());
        var wbottom = (int) (windowFBHeight * 0.5f + scale * this.context.height());
        GlState.bindDrawFramebuffer(0);
        GlState.bindReadFramebuffer(this.framebuffer);
        final var colour = this.context.colourScheme().background();
        GlState.clearColor(colour.redf(), colour.greenf(), colour.bluef(), 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        // src Y are flipped, since our FB is flipped
        glBlitFramebuffer(0, this.context.height() * this.context.scale(), this.context.width() * this.context.scale(), 0, RenderElement.clamp(wleft, 0, windowFBWidth), RenderElement.clamp(wtop, 0, windowFBHeight), RenderElement.clamp(wright, 0, windowFBWidth), RenderElement.clamp(wbottom, 0, windowFBHeight), GL_COLOR_BUFFER_BIT, GL_NEAREST);
        GlState.bindFramebuffer(0);
    }

    int getTexture() {
        return this.texture;
    }

    public void close() {
        glDeleteTextures(this.texture);
        glDeleteFramebuffers(this.framebuffer);
    }
}
