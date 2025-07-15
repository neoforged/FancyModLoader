/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.error;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.theme.ImageLoader;
import net.neoforged.fml.earlydisplay.theme.NativeBuffer;
import net.neoforged.fml.earlydisplay.theme.TextureScaling;
import net.neoforged.fml.earlydisplay.theme.UncompressedImage;
import org.lwjgl.system.MemoryUtil;

record Button(ErrorDisplayWindow window, int x, int y, int width, int height, String text, Runnable onPress) {
    void render(RenderContext ctx, SimpleFont font, double mouseX, double mouseY) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        Texture texture = hovered ? window.buttonTextureHover : window.buttonTexture;
        ctx.blitTexture(texture, x, y, width, height);

        int w = font.stringWidth(text);
        float tx = x + width / 2F - w / 2F;
        ctx.renderTextWithShadow(tx, y + 2, font, List.of(new SimpleFont.DisplayText(text, 0xFFFFFFFF)));
    }

    boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static Texture loadTexture(boolean highlighted) {
        String suffix = highlighted ? "_highlighted" : "";
        String fileName = "assets/minecraft/textures/gui/sprites/widget/button" + suffix + ".png";
        String debugName = "error_button" + suffix;

        Texture texture;
        try {
            NativeBuffer buffer = NativeBuffer.loadFromClasspath(fileName, null);
            ImageLoader.Result result = ImageLoader.tryLoadImage(debugName, null, buffer);
            texture = switch (result) {
                case ImageLoader.Result.Success success -> {
                    // Sizes are deliberately double the real values to ensure the buttons render identical to vanilla in an environment with double the resolution
                    TextureScaling scaling = new TextureScaling.NineSlice(400, 40, 6, 6, 6, 6, false, false, false);
                    yield Texture.create(success.image(), debugName, scaling, null);
                }
                case ImageLoader.Result.Error ignored -> createFallbackTexture(highlighted, suffix);
            };
        } catch (IOException e) {
            texture = createFallbackTexture(highlighted, suffix);
        }
        return texture;
    }

    private static Texture createFallbackTexture(boolean highlighted, String suffix) {
        String name = "error_button_fallback" + suffix;
        int width = 60;
        int height = 20;
        ByteBuffer pixelData = MemoryUtil.memAlloc(width * height * 4);
        IntBuffer pixelBuffer = pixelData.asIntBuffer(); // ABGR format
        int borderColor = highlighted ? 0xFFFFFFFF : 0xFF000000;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    pixelBuffer.put(borderColor);
                } else {
                    pixelBuffer.put(0xFF888888);
                }
            }
        }
        NativeBuffer buffer = new NativeBuffer(pixelData, MemoryUtil::memFree);
        UncompressedImage image = new UncompressedImage(name, null, buffer, width, height);
        TextureScaling scaling = new TextureScaling.NineSlice(width, height, 1, 1, 1, 1, true, true, false);
        return Texture.create(image, name, scaling, null);
    }
}
