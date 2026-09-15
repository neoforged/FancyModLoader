/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.nio.file.Path;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.ELSTexture;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;
import net.neoforged.fml.earlydisplay.theme.AnimationMetadata;
import net.neoforged.fml.earlydisplay.theme.TextureScaling;
import net.neoforged.fml.earlydisplay.theme.ThemeTexture;
import net.neoforged.fml.earlydisplay.theme.UncompressedImage;
import org.jetbrains.annotations.Nullable;

public record Texture(ELSTexture texture, TextureScaling scaling, @Nullable AnimationMetadata animationMetadata) implements AutoCloseable {
    public int width() {
        return scaling.width();
    }

    public int height() {
        return scaling.height();
    }

    public int physicalWidth() {
        return this.texture.width();
    }

    public int physicalHeight() {
        return this.texture.height();
    }

    /**
     * Loads a resource into an OpenGL texture.
     */
    public static Texture create(ELSRenderBackend backend, ThemeTexture themeTexture, @Nullable Path externalThemeDirectory) {
        try (var image = themeTexture.resource().loadAsImage(externalThemeDirectory)) {
            return create(backend, image, "EarlyDisplay " + themeTexture, themeTexture.scaling(), themeTexture.animation());
        }
    }

    /// Create a texture from the provided image.
    ///
    /// @param image     The image to write to the texture
    /// @param debugName The name to use as GL debug label of the texture
    /// @param scaling   The scaling to apply to the texture
    /// @param animation The animation, if any, to render the texture with
    public static Texture create(
            ELSRenderBackend backend,
            UncompressedImage image,
            String debugName,
            TextureScaling scaling,
            @Nullable AnimationMetadata animation) {
        ELSTexture texture = backend.createTexture(debugName, image.width(), image.height(), TextureFormat.RGBA, scaling.linearScaling());
        backend.writeToTexture(texture, image.imageData());
        return new Texture(texture, scaling, animation);
    }

    @Override
    public void close() {
        this.texture.close();
    }
}
