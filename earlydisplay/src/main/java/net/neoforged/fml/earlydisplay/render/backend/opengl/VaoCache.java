package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.VertexFormat;
import org.lwjgl.opengl.GL33C;

import java.util.EnumMap;
import java.util.Map;

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
            VertexFormat.Element element = format.element(i);
            if (enable) {
                GL33C.glEnableVertexAttribArray(i);
            }
            switch (element) {
                case POS, TEX -> GL33C.glVertexAttribPointer(i, element.count, GL33C.GL_FLOAT, false, stride, offset);
                case COLOR -> GL33C.glVertexAttribPointer(i, element.count, GL33C.GL_UNSIGNED_BYTE, true, stride, offset);
            }
            offset += element.width;
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
