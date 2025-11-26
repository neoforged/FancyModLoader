/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;

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
        var texId = glGenTextures();
        GlState.bindTexture2D(texId);
        GlDebug.labelTexture(texId, debugName);
        boolean linear = scaling.linearScaling();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width(), image.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image.imageData());
        return new Texture(texId, image.width(), image.height(), scaling, animation);
    }

    @Override
    public void close() {
        GL32C.glDeleteTextures(textureId);
    }
}
