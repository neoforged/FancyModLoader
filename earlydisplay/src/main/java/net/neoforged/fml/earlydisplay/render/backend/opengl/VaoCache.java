/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import java.util.EnumMap;
import java.util.Map;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;
import net.neoforged.fml.earlydisplay.render.backend.VertexFormat;
import org.lwjgl.opengl.GL33C;

final class VaoCache implements AutoCloseable {
    private final Map<VertexFormat, VAO> cache = new EnumMap<>(VertexFormat.class);

    VAO bindVertexBuffer(VertexFormat format, GlBufferSlice vertexBuffer) {
        VAO vao = this.cache.get(format);
        boolean enable = false;
        if (vao == null) {
            int id = GL33C.glGenVertexArrays();
            GlState.bindVertexArray(id);
            GlDebug.labelVertexArray(id, format.name());
            vao = new VAO(id);
            this.cache.put(format, vao);
            enable = true;
        } else {
            GlState.bindVertexArray(vao.id);
        }

        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vertexBuffer.buffer().bufferId);
        int stride = format.stride;
        long offset = vertexBuffer.offset();
        for (int i = 0; i < format.elementCount(); i++) {
            if (enable) {
                GL33C.glEnableVertexAttribArray(i);
            }
            TextureFormat elementFormat = format.element(i).format;
            GL33C.glVertexAttribPointer(i, elementFormat.getComponents(), GlConst.toGlType(elementFormat), elementFormat.getComponentType().isNormalized(), stride, offset);
            offset += elementFormat.getSize();
        }

        return vao;
    }

    @Override
    public void close() {
        cache.values().forEach(VAO::close);
    }

    record VAO(int id) implements AutoCloseable {
        @Override
        public void close() {
            GL33C.glDeleteVertexArrays(id);
        }
    }
}
