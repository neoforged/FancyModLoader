package net.neoforged.fml.earlydisplay.theme;

import java.nio.ByteBuffer;

/**
 * Image data loaded into memory and decompressed.
 */
public record UncompressedImage(String name, NativeBuffer nativeImageData, int width,
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
