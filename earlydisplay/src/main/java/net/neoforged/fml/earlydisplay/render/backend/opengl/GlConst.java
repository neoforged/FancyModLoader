/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import java.util.Set;
import net.neoforged.fml.earlydisplay.render.backend.ELSBuffer;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;
import org.lwjgl.opengl.GL33C;

final class GlConst {
    static int toGlInternalId(TextureFormat format) {
        return switch (format) {
            case RGBA8_UNORM -> GL33C.GL_RGBA8;
            case RED8_UNORM -> GL33C.GL_R8;
            case RG32_FLOAT -> GL33C.GL_RG32F;
        };
    }

    static int toGlExternalId(TextureFormat format) {
        return switch (format) {
            case RGBA8_UNORM -> GL33C.GL_RGBA;
            case RED8_UNORM -> GL33C.GL_RED;
            case RG32_FLOAT -> GL33C.GL_RG;
        };
    }

    static int toGlType(TextureFormat format) {
        return switch (format.getComponentType()) {
            case UNORM_8 -> GL33C.GL_UNSIGNED_BYTE;
            case FLOAT_32 -> GL33C.GL_FLOAT;
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

    private GlConst() {}
}
