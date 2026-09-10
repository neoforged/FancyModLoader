/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import java.util.Set;
import net.neoforged.fml.earlydisplay.render.backend.ELSBuffer;
import org.lwjgl.opengl.GL33C;

final class GlBuffer implements ELSBuffer {
    final int bufferId;
    private final Set<Usage> usage;
    private final long size;
    private final GlBufferSlice defaultSlice;

    GlBuffer(int bufferId, Set<Usage> usage, long size) {
        this.bufferId = bufferId;
        this.usage = usage;
        this.size = size;
        this.defaultSlice = new GlBufferSlice(this, 0, size);
    }

    @Override
    public Set<Usage> usage() {
        return usage;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public GlBufferSlice slice() {
        return this.defaultSlice;
    }

    @Override
    public GlBufferSlice slice(long offset, long length) {
        return new GlBufferSlice(this, offset, length);
    }

    @Override
    public void close() {
        GL33C.glDeleteBuffers(this.bufferId);
    }
}
