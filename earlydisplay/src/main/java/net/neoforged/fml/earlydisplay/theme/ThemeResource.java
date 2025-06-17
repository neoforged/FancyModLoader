/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

public record ThemeResource(String path) {
    public NativeBuffer toNativeBuffer(@Nullable Path externalThemeDirectory) throws IOException {
        if (externalThemeDirectory != null) {
            try {
                return NativeBuffer.loadFromPath(externalThemeDirectory.resolve(path));
            } catch (NoSuchFileException ignored) {
                // Fall through and load fallback resource from built-in resources
            }
        }

        return NativeBuffer.loadFromClasspath("net/neoforged/fml/earlydisplay/theme/" + path, null);
    }

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    public UncompressedImage loadAsImage(@Nullable Path externalThemeDirectory) {
        return ImageLoader.loadImage(this, externalThemeDirectory);
    }

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, null is returned.
     */
    @Nullable
    public UncompressedImage tryLoadAsImage(@Nullable Path externalThemeDirectory) {
        return switch (ImageLoader.tryLoadImage(this, externalThemeDirectory)) {
            case ImageLoader.Result.Error error -> null;
            case ImageLoader.Result.Success(UncompressedImage image) -> image;
        };
    }
}
