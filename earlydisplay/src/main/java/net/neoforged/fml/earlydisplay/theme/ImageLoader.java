/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an image referenced from a theme.
 * <p>
 * Theme images will refer to a resource their content is loaded from, this is expected to be a PNG image.
 */
public final class ImageLoader {
    private static final String BROKEN_TEXTURE_NAME = "broken texture";

    private static final int BROKEN_TEXTURE_DIMENSIONS = 16;

    static final Logger LOGGER = LoggerFactory.getLogger(ImageLoader.class);

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    public static UncompressedImage loadImage(ThemeResource resource, @Nullable Path externalThemeDirectory) {
        return switch (tryLoadImage(resource, externalThemeDirectory)) {
            case Result.Success(UncompressedImage image) -> image;
            case Result.Error(Exception exception) -> {
                LOGGER.error("Failed to load theme image {}", resource, exception);
                yield createBrokenImage();
            }
        };
    }

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    public static Result tryLoadImage(ThemeResource resource, @Nullable Path externalThemeDirectory) {
        try (var buffer = resource.toNativeBuffer(externalThemeDirectory)) {
            return tryLoadImage(resource.toString(), resource, buffer);
        } catch (Exception e) {
            return new Result.Error(e);
        }
    }

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    public static Result tryLoadImage(String debugName, @Nullable ThemeResource source, NativeBuffer buffer) {
        try {
            validateHeader(buffer.buffer().slice());

            var width = new int[1];
            var height = new int[1];
            var channels = new int[1];
            var decodedImage = STBImage.stbi_load_from_memory(buffer.buffer(), width, height, channels, 4);
            // TODO: Handle image decoding error
            return new Result.Success(new UncompressedImage(
                    debugName,
                    source,
                    new NativeBuffer(decodedImage, STBImage::stbi_image_free),
                    width[0],
                    height[0]));
        } catch (Exception e) {
            return new Result.Error(e);
        }
    }

    // Taken from Mojangs code to add validation that STB doesnt seem to have.
    private static void validateHeader(ByteBuffer buffer) throws IOException {
        ByteOrder byteorder = buffer.order();
        buffer.order(ByteOrder.BIG_ENDIAN);
        if (buffer.getLong(0) != 0x89504e470d0a1a0aL) {
            throw new IOException("Bad PNG Signature");
        } else if (buffer.getInt(8) != 13) {
            throw new IOException("Bad length for IHDR chunk!");
        } else if (buffer.getInt(12) != 0x49484452) {
            throw new IOException("Bad type for IHDR chunk!");
        } else {
            buffer.order(byteorder);
        }
    }

    public sealed interface Result {
        record Success(UncompressedImage image) implements Result {}

        record Error(Exception exception) implements Result {}
    }

    private static UncompressedImage createBrokenImage() {
        var pixelData = MemoryUtil.memAlloc(BROKEN_TEXTURE_DIMENSIONS * BROKEN_TEXTURE_DIMENSIONS * 4);
        var pixelBuffer = pixelData.asIntBuffer(); // ABGR format

        for (var y = 0; y < BROKEN_TEXTURE_DIMENSIONS; y++) {
            for (var x = 0; x < BROKEN_TEXTURE_DIMENSIONS; x++) {
                if (x < BROKEN_TEXTURE_DIMENSIONS / 2 ^ y < BROKEN_TEXTURE_DIMENSIONS / 2) {
                    pixelBuffer.put(0xFFF800F8);
                } else {
                    pixelBuffer.put(0xFF000000);
                }
            }
        }

        var nativeBuffer = new NativeBuffer(pixelData, MemoryUtil::memFree);
        return new UncompressedImage(
                BROKEN_TEXTURE_NAME,
                null,
                nativeBuffer,
                BROKEN_TEXTURE_DIMENSIONS,
                BROKEN_TEXTURE_DIMENSIONS);
    }

    private ImageLoader() {}
}
