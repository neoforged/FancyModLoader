/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an image referenced from a theme.
 * <p>
 * Theme images will refer to a resource their content is loaded from, this is expected to be a PNG image.
 */
final class ImageLoader {
    private static final int BROKEN_TEXTURE_DIMENSIONS = 16;
    static final Logger LOGGER = LoggerFactory.getLogger(ImageLoader.class);

    /**
     * Load the image resource, and decompress it into native memory for use with OpenGL and other native APIs.
     * Note that if the image fails to load for any reason, a dummy "missing" texture is returned instead.
     */
    static UncompressedImage loadImage(ThemeResource resource) {
        try (var buffer = resource.toNativeBuffer()) {
            var width = new int[1];
            var height = new int[1];
            var channels = new int[1];
            var decodedImage = STBImage.stbi_load_from_memory(buffer.buffer(), width, height, channels, 4);
            // TODO: Handle image decoding error
            return new UncompressedImage(resource.toString(),
                    resource,
                    new NativeBuffer(decodedImage, STBImage::stbi_image_free),
                    width[0],
                    height[0]);
        } catch (Exception e) {
            LOGGER.error("Failed to load theme image {}", resource, e);
            return createBrokenImage();
        }
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
                "broken texture",
                null,
                nativeBuffer,
                BROKEN_TEXTURE_DIMENSIONS,
                BROKEN_TEXTURE_DIMENSIONS);
    }

    private ImageLoader() {}
}
