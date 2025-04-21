/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.io.IOException;

public sealed interface ThemeResource permits ClasspathResource, FileResource {
    /**
     * Sanity check to stop going OOM instead of showing the loading screen.
     */
    int MAX_SIZE = 100_000_000;

    NativeBuffer toNativeBuffer() throws IOException;

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    default UncompressedImage loadAsImage() {
        return ImageLoader.loadImage(this);
    }
}
