package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;

import net.neoforged.fml.earlydisplay.theme.AnimationMetadata;
import net.neoforged.fml.earlydisplay.theme.TextureScaling;
import net.neoforged.fml.earlydisplay.theme.ThemeTexture;
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
    public static Texture create(ThemeTexture themeTexture) {
        try (var image = themeTexture.resource().loadAsImage()) {
            var texId = glGenTextures();
            GlState.activeTexture(GL_TEXTURE0);
            GlState.bindTexture2D(texId);
            GlDebug.labelTexture(texId, "EarlyDisplay " + themeTexture);
            boolean linear = themeTexture.scaling().linearScaling();
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width(), image.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image.imageData());
            GlState.activeTexture(GL_TEXTURE0);
            return new Texture(texId, image.width(), image.height(), themeTexture.scaling(), themeTexture.animation());
        }
    }

    @Override
    public void close() {
        GL32C.glDeleteTextures(textureId);
    }
}
