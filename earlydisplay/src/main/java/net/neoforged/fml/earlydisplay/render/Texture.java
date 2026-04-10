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

    /// Create a texture from the provided image.
    ///
    /// @param image     The image to write to the texture
    /// @param debugName The name to use as GL debug label of the texture
    /// @param scaling   The scaling to apply to the texture
    /// @param animation The animation, if any, to render the texture with
    public static Texture create(
            UncompressedImage image,
            String debugName,
            TextureScaling scaling,
            @Nullable AnimationMetadata animation) {
        // Initializing the texture (via glTexImage2D() with a null buffer) and writing its contents (via glTexSubImage2D())
        // has to happen separately as doing both in one glTexImage2D() call may cause segfaults in some cases,
        // particularly if invoked after vanilla has initialized its renderer and started creating its own textures.
        int texId = createEmpty(debugName, image.width(), image.height(), GL_RGBA8, GL_RGBA, scaling.linearScaling());
        writeToTexture(texId, image.width(), image.height(), GL_RGBA, 4, image.imageData());
        return new Texture(texId, image.width(), image.height(), scaling, animation);
    }

    /// Create an empty GL texture with the specified parameters.
    ///
    /// @param width          The width of the texture in pixels
    /// @param height         The height of the texture in pixels
    /// @param internalFormat The internal GL format
    /// @param externalFormat The external GL format
    /// @param linearFilter   Whether the texture should use linear or nearest-neighbor filtering
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

    /// Write the provided pixel buffer's contents to the specified texture.
    ///
    /// @param textureId      The GL ID of the target texture
    /// @param width          The width of the texture in pixels
    /// @param height         The height of the texture in pixels
    /// @param externalFormat The external GL format
    /// @param components     The amount of components per pixel
    /// @param pixels         The pixel data to write to the texture
    public static void writeToTexture(
            int textureId,
            int width,
            int height,
            int externalFormat,
            int components,
            ByteBuffer pixels) {
        GlState.bindTexture2D(textureId);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, width);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, components);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, externalFormat, GL_UNSIGNED_BYTE, pixels);
    }

    @Override
    public void close() {
        GL32C.glDeleteTextures(textureId);
    }
}
