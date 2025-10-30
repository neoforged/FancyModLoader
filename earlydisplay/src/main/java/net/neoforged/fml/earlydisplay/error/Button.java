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

final class Button {
    private final ErrorDisplayWindow window;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String text;
    private final boolean active;
    private final Runnable onPress;
    private boolean focused;

    Button(ErrorDisplayWindow window, int x, int y, int width, int height, String text, boolean active, Runnable onPress) {
        this.window = window;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.active = active;
        this.onPress = onPress;
    }

    void render(RenderContext ctx, SimpleFont font, double mouseX, double mouseY) {
        boolean highlighted = active && (focused || isMouseOver(mouseX, mouseY));
        Texture texture = active ? (highlighted ? window.buttonTextureHover : window.buttonTexture) : window.buttonTextureInactive;
        ctx.blitTexture(texture, x, y, width, height);

        int w = font.stringWidth(text);
        float tx = x + width / 2F - w / 2F;
        int textColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        ctx.renderTextWithShadow(tx, y + 2, font, List.of(new SimpleFont.DisplayText(text, textColor)));
    }

    boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    boolean isActive() {
        return active;
    }

    boolean isFocused() {
        return focused;
    }

    void focus() {
        this.focused = true;
    }

    void unfocus() {
        focused = false;
    }

    void press() {
        if (this.active) {
            this.onPress.run();
        }
    }

    static Texture loadTexture(boolean active, boolean highlighted) {
        String suffix = active ? (highlighted ? "_highlighted" : "") : "_disabled";
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
                case ImageLoader.Result.Error ignored -> createFallbackTexture(active, highlighted, suffix);
            };
        } catch (IOException e) {
            texture = createFallbackTexture(active, highlighted, suffix);
        }
        return texture;
    }

    private static Texture createFallbackTexture(boolean active, boolean highlighted, String suffix) {
        String name = "error_button_fallback" + suffix;
        int width = 60;
        int height = 20;
        ByteBuffer pixelData = MemoryUtil.memAlloc(width * height * 4);
        IntBuffer pixelBuffer = pixelData.asIntBuffer(); // ABGR format
        int borderColor = highlighted ? 0xFFFFFFFF : 0xFF000000;
        int fillColor = active ? 0xFF888888 : 0xFF2C2C2C;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    pixelBuffer.put(borderColor);
                } else {
                    pixelBuffer.put(fillColor);
                }
            }
        }
        NativeBuffer buffer = new NativeBuffer(pixelData, MemoryUtil::memFree);
        UncompressedImage image = new UncompressedImage(name, null, buffer, width, height);
        // Sizes are deliberately double the real values to ensure the buttons render identical to vanilla in an environment with double the resolution
        TextureScaling scaling = new TextureScaling.NineSlice(width * 2, height * 2, 2, 2, 2, 2, true, true, false);
        return Texture.create(image, name, scaling, null);
    }
}
