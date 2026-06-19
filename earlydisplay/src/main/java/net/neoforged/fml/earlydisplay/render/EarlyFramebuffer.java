/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.ELSTexture;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;

public final class EarlyFramebuffer {
    private final ELSRenderBackend backend;
    private ELSTexture texture;
    private int width;
    private int height;

    public EarlyFramebuffer(ELSRenderBackend backend, int width, int height) {
        this.backend = backend;
        this.width = width;
        this.height = height;
        this.texture = backend.createTexture("EarlyDisplay Framebuffer", width, height, TextureFormat.RGBA, false);
    }

    public ELSTexture texture() {
        return this.texture;
    }

    public void resize(int width, int height) {
        if (this.width != width || this.height != height) {
            this.texture.close();
            this.texture = this.backend.createTexture("EarlyDisplay Framebuffer", width, height, TextureFormat.RGBA, false);
            this.width = width;
            this.height = height;
        }
    }

    public void close() {
        this.texture.close();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
