/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33C;

public record GlSampler(int id) {
    public static GlSampler create() {
        int id = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        return new GlSampler(id);
    }
}
