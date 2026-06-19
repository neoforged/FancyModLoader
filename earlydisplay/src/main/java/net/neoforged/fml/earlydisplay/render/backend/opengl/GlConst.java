package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSBuffer;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;
import org.lwjgl.opengl.GL33C;

import java.util.Set;

final class GlConst {
    static int toGlInternalId(TextureFormat format) {
        return switch (format) {
            case RGBA -> GL33C.GL_RGBA8;
            case RED -> GL33C.GL_R8;
        };
    }

    static int toGlExternalId(TextureFormat format) {
        return switch (format) {
            case RGBA -> GL33C.GL_RGBA;
            case RED -> GL33C.GL_RED;
        };
    }

    static int getBufferBindTarget(Set<ELSBuffer.Usage> usage) {
        if (usage.contains(ELSBuffer.Usage.VERTEX)) {
            return GL33C.GL_ARRAY_BUFFER;
        }
        if (usage.contains(ELSBuffer.Usage.INDEX)) {
            return GL33C.GL_ELEMENT_ARRAY_BUFFER;
        }
        if (usage.contains(ELSBuffer.Usage.UNIFORM)) {
            return GL33C.GL_UNIFORM_BUFFER;
        }
        return GL33C.GL_COPY_WRITE_BUFFER;
    }

    static int bufferUsageToGlEnum(Set<ELSBuffer.Usage> usage) {
        boolean clientStorage = usage.contains(ELSBuffer.Usage.HINT_CLIENT_STORAGE);
        if (usage.contains(ELSBuffer.Usage.MAP_WRITE)) {
            return clientStorage ? GL33C.GL_STREAM_DRAW : GL33C.GL_STATIC_DRAW;
        }
        if (usage.contains(ELSBuffer.Usage.MAP_READ)) {
            return clientStorage ? GL33C.GL_STREAM_READ : GL33C.GL_STATIC_READ;
        }
        return GL33C.GL_STATIC_DRAW;
    }

    private GlConst() { }
}
