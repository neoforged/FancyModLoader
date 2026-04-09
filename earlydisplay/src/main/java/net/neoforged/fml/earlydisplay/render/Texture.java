/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glTexSubImage2D;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import net.neoforged.fml.earlydisplay.theme.AnimationMetadata;
import net.neoforged.fml.earlydisplay.theme.TextureScaling;
import net.neoforged.fml.earlydisplay.theme.ThemeTexture;
import net.neoforged.fml.earlydisplay.theme.UncompressedImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32C;

public record Texture(int textureId, int physicalWidth, int physicalHeight,
        TextureScaling scaling,
        @Nullable AnimationMetadata animationMetadata) implements AutoCloseable {
    public int width() {
        return scaling.width();
    }

    public int height() {
        return scaling.height();
    }

    /**
     * Loads a resource into an OpenGL texture.
     */
    public static Texture create(ThemeTexture themeTexture, @Nullable Path externalThemeDirectory) {
        try (var image = themeTexture.resource().loadAsImage(externalThemeDirectory)) {
            return create(image, "EarlyDisplay " + themeTexture, themeTexture.scaling(), themeTexture.animation());
        }
    }

    public static Texture create(
            UncompressedImage image,
            String debugName,
            TextureScaling scaling,
            @Nullable AnimationMetadata animation) {
        int texId = createEmpty(debugName, image.width(), image.height(), GL_RGBA8, GL_RGBA, scaling.linearScaling());
        glPixelStorei(GL_UNPACK_ROW_LENGTH, image.width());
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, image.width(), image.height(), GL_RGBA, GL_UNSIGNED_BYTE, image.imageData());
        return new Texture(texId, image.width(), image.height(), scaling, animation);
    }

    public static int createEmpty(
            String debugName,
            int width,
            int height,
            int internalFormat,
            int externalFormat,
            boolean linearFilter) {
        int texId = glGenTextures();
        GlState.bindTexture2D(texId);
        GlDebug.labelTexture(texId, debugName);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linearFilter ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linearFilter ? GL_LINEAR : GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, externalFormat, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return texId;
    }

    @Override
    public void close() {
        GL32C.glDeleteTextures(textureId);
    }
}
