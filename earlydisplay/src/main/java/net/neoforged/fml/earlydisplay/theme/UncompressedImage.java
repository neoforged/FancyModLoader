/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.Nullable;

/**
 * Image data loaded into memory and decompressed.
 */
public record UncompressedImage(
        String name,
        @Nullable ThemeResource source,
        NativeBuffer nativeImageData,
        int width,
        int height) implements AutoCloseable {
    public ByteBuffer imageData() {
        return nativeImageData.buffer();
    }

    @Override
    public void close() {
        nativeImageData.close();
    }

    @Override
    public String toString() {
        return name;
    }
}
