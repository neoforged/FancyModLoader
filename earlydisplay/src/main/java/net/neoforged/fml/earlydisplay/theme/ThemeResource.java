/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

public record ThemeResource(@Nullable Path themeDirectory, String path) {
    public ThemeResource(String path) {
        this(null, path);
    }

    public NativeBuffer toNativeBuffer() throws IOException {
        if (themeDirectory != null) {
            try {
                return NativeBuffer.loadFromPath(themeDirectory.resolve(path));
            } catch (NoSuchFileException ignored) {
                // Fall through and load fallback resource from built-in resources
            }
        }

        return NativeBuffer.loadFromClasspath("net/neoforged/fml/earlydisplay/theme/" + path);
    }

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    public UncompressedImage loadAsImage() {
        return ImageLoader.loadImage(this);
    }

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    @Nullable
    public UncompressedImage tryLoadAsImage() {
        return switch (ImageLoader.tryLoadImage(this)) {
            case ImageLoader.Result.Error error -> null;
            case ImageLoader.Result.Success(UncompressedImage image) -> image;
        };
    }
}
