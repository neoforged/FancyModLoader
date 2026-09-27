/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSTexture;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;
import org.lwjgl.opengl.GL33C;

final class GlTexture implements ELSTexture {
    private final String name;
    private final int width;
    private final int height;
    private final TextureFormat format;
    final int textureId;
    private int fboId = -1;
    private boolean closed;

    GlTexture(String name, int width, int height, TextureFormat format, int textureId) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.format = format;
        this.textureId = textureId;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public TextureFormat format() {
        return this.format;
    }

    public int fbo() {
        if (this.fboId == -1) {
            this.fboId = GL33C.glGenFramebuffers();
            GlState.bindFramebuffer(this.fboId);
            GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, this.textureId, 0);
            GlState.bindFramebuffer(0);
            GlDebug.labelFramebuffer(this.fboId, this.name);
        }
        return this.fboId;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (this.fboId != -1) {
            GL33C.glDeleteFramebuffers(this.fboId);
        }
        GL33C.glDeleteTextures(this.textureId);
    }

    @Override
    public String toString() {
        return "GlTexture{" + this.name + "}";
    }
}
