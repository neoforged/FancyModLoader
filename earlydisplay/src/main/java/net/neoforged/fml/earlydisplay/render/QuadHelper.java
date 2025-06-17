/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import net.neoforged.fml.earlydisplay.theme.TextureScaling;
import net.neoforged.fml.earlydisplay.util.Bounds;

class QuadHelper {
    public static void fillSprite(SimpleBufferBuilder buffer,
            Texture texture,
            float x,
            float y,
            float z,
            float width,
            float height,
            int color,
            SpriteFillDirection fillDirection,
            int animationFrame,
            float srcU0,
            float srcU1,
            float srcV0,
            float srcV1) {
        // Too large values for width / height cause immediate crashes of the VM due to graphics driver bugs<
        // These maximum values are picked without too much thought.
        width = Math.min(65535, width);
        height = Math.min(65535, height);

        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (texture.animationMetadata() != null) {
            int frameCount = texture.animationMetadata().frameCount();
            var frameHeight = texture.physicalHeight() / frameCount;
            var vUnit = frameHeight / (float) texture.physicalHeight();
            v0 = (animationFrame % frameCount) * vUnit;
            v1 = (animationFrame % frameCount + 1) * vUnit;
        }

        // Apply a source region of the texture if requested
        var w = (u1 - u0);
        u1 = u0 + w * srcU1;
        u0 = u0 + w * srcU0;
        var h = (v1 - v0);
        v1 = v0 + h * srcV1;
        v0 = v0 + h * srcV0;

        switch (texture.scaling()) {
            case TextureScaling.Tile tiled -> {
                fillTiled(buffer, x, y, z, width, height, color, tiled.width(), tiled.height(), u0, u1, v0, v1, fillDirection);
            }
            case TextureScaling.Stretch stretch -> {
                addQuad(buffer, x, y, z, width, height, color, u0, u1, v0, v1);
            }
            case TextureScaling.NineSlice nineSlice -> {
                addTiledNineSlice(buffer, x, y, z, width, height, color, nineSlice, u0, u1, v0, v1);
            }
            default -> {}
        }
    }

    private static void addTiledNineSlice(SimpleBufferBuilder buffer,
            float x,
            float y,
            float z,
            float width,
            float height,
            int color,
            TextureScaling.NineSlice nineSlice,
            float u0,
            float u1,
            float v0,
            float v1) {
        var leftWidth = Math.min(nineSlice.left(), width / 2);
        var rightWidth = Math.min(nineSlice.right(), width / 2);
        var topHeight = Math.min(nineSlice.top(), height / 2);
        var bottomHeight = Math.min(nineSlice.bottom(), height / 2);
        var innerWidth = nineSlice.width() - nineSlice.left() - nineSlice.right();
        var innerHeight = nineSlice.height() - nineSlice.top() - nineSlice.bottom();

        // The U/V values for the cuts through the nine-slice we'll use
        var leftU = u0 + leftWidth / nineSlice.width() * (u1 - u0);
        var rightU = u1 - rightWidth / nineSlice.width() * (u1 - u0);
        var topV = v0 + topHeight / nineSlice.height() * (v1 - v0);
        var bottomV = v1 - bottomHeight / nineSlice.height() * (v1 - v0);

        // Destination pixel values of the inner rectangle
        var dstInnerLeft = x + leftWidth;
        var dstInnerTop = y + topHeight;
        var dstInnerRight = x + width - rightWidth;
        var dstInnerBottom = y + height - bottomHeight;
        var dstInnerWidth = dstInnerRight - dstInnerLeft;
        var dstInnerHeight = dstInnerBottom - dstInnerTop;

        // Corners are always untiled, but may be cropped
        addQuad(buffer, x, y, z, leftWidth, topHeight, color, u0, leftU, v0, topV); // Top left
        addQuad(buffer, dstInnerRight, y, z, rightWidth, topHeight, color, rightU, u1, v0, topV); // Top right
        addQuad(buffer, dstInnerRight, dstInnerBottom, z, rightWidth, bottomHeight, color, rightU, u1, bottomV, v1); // Bottom
        // right
        addQuad(buffer, x, dstInnerBottom, z, leftWidth, bottomHeight, color, u0, leftU, bottomV, v1); // Bottom left

        // The edges can be tiled
        if (nineSlice.stretchHorizontalFill()) {
            addQuad(buffer, dstInnerLeft, y, z, dstInnerWidth, topHeight, color, leftU, rightU, v0, topV); // Top Edge
            addQuad(buffer, dstInnerLeft, dstInnerBottom, z, dstInnerWidth, bottomHeight, color, leftU, rightU, bottomV, v1); // Bottom Edge
        } else {
            fillTiled(buffer, dstInnerLeft, y, z, dstInnerWidth, topHeight, color, innerWidth, nineSlice.top(), leftU, rightU, v0,
                    topV); // Top Edge
            fillTiled(buffer, dstInnerLeft, dstInnerBottom, z, dstInnerWidth, bottomHeight, color, innerWidth, nineSlice.bottom(),
                    leftU, rightU, bottomV, v1); // Bottom Edge
        }
        if (nineSlice.stretchVerticalFill()) {
            addQuad(buffer, x, dstInnerTop, z, leftWidth, dstInnerHeight, color, u0, leftU, topV, bottomV); // Left Edge
            addQuad(buffer, dstInnerRight, dstInnerTop, z, rightWidth, dstInnerHeight, color, rightU, u1, topV, bottomV); // Right Edge
        } else {
            fillTiled(buffer, x, dstInnerTop, z, leftWidth, dstInnerHeight, color, nineSlice.left(), innerHeight, u0, leftU, topV,
                    bottomV); // Left Edge
            fillTiled(buffer, dstInnerRight, dstInnerTop, z, rightWidth, dstInnerHeight, color, nineSlice.right(), innerHeight, rightU,
                    u1, topV, bottomV); // Right Edge
        }

        // The center is tiled too
        if (nineSlice.stretchHorizontalFill() && nineSlice.stretchVerticalFill()) {
            addQuad(buffer, dstInnerLeft, dstInnerTop, z, dstInnerWidth, dstInnerHeight, color, leftU, rightU, topV, bottomV);
        } else if (nineSlice.stretchHorizontalFill()) {
            fillTiled(buffer, dstInnerLeft, dstInnerTop, z, dstInnerWidth, dstInnerHeight, color, dstInnerWidth, innerHeight, leftU,
                    rightU, topV, bottomV);
        } else if (nineSlice.stretchVerticalFill()) {
            fillTiled(buffer, dstInnerLeft, dstInnerTop, z, dstInnerWidth, dstInnerHeight, color, innerWidth, dstInnerHeight, leftU,
                    rightU, topV, bottomV);
        } else {
            fillTiled(buffer, dstInnerLeft, dstInnerTop, z, dstInnerWidth, dstInnerHeight, color, innerWidth, innerHeight, leftU,
                    rightU, topV, bottomV);
        }
    }

    private static void fillTiled(SimpleBufferBuilder buffer, float x, float y, float z, float width, float height, int color, float destTileWidth,
            float destTileHeight, float u0, float u1, float v0, float v1) {
        fillTiled(buffer, x, y, z, width, height, color, destTileWidth, destTileHeight, u0, u1, v0, v1,
                SpriteFillDirection.TOP_TO_BOTTOM);
    }

    private static void fillTiled(SimpleBufferBuilder buffer, float x, float y, float z, float width, float height, int color, float destTileWidth,
            float destTileHeight, float u0, float u1, float v0, float v1, SpriteFillDirection fillDirection) {
        if (destTileWidth <= 0 || destTileHeight <= 0) {
            return;
        }

        var right = x + width;
        var bottom = y + height;

        if (fillDirection == SpriteFillDirection.BOTTOM_TO_TOP) {
            for (var cy = bottom; cy >= y; cy -= destTileHeight) {
                // This handles not stretching the potentially partial last column
                var tileHeight = Math.min(cy - y, destTileHeight);
                var tileV0 = v1 - (v1 - v0) * tileHeight / destTileHeight;

                for (var cx = x; cx < right; cx += destTileWidth) {
                    // This handles not stretching the potentially partial last row
                    var tileWidth = Math.min(right - cx, destTileWidth);
                    var tileU1 = u0 + (u1 - u0) * tileWidth / destTileWidth;

                    addQuad(buffer, cx, cy - tileHeight, z, tileWidth, tileHeight, color, u0, tileU1, tileV0, v1);
                }
            }
        } else {
            for (var cy = y; cy < bottom; cy += destTileHeight) {
                // This handles not stretching the potentially partial last column
                var tileHeight = Math.min(bottom - cy, destTileHeight);
                var tileV1 = v0 + (v1 - v0) * tileHeight / destTileHeight;

                for (var cx = x; cx < right; cx += destTileWidth) {
                    // This handles not stretching the potentially partial last row
                    var tileWidth = Math.min(right - cx, destTileWidth);
                    var tileU1 = u0 + (u1 - u0) * tileWidth / destTileWidth;

                    addQuad(buffer, cx, cy, z, tileWidth, tileHeight, color, u0, tileU1, v0, tileV1);
                }
            }
        }
    }

    public static void addQuad(SimpleBufferBuilder buffer, float x, float y, float z, float width, float height, int color, float minU, float maxU,
            float minV, float maxV) {
        if (width < 0 || height < 0) {
            return;
        }

        loadQuad(buffer, x, x + width, y, y + height, minU, maxU, minV, maxV, color);
    }

    public static void loadQuad(SimpleBufferBuilder bb, Bounds bounds, float u0, float u1, float v0, float v1, int colour) {
        loadQuad(bb, bounds.left(), bounds.right(), bounds.top(), bounds.bottom(), u0, u1, v0, v1, colour);
    }

    public static void loadQuad(SimpleBufferBuilder bb, float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1, int colour) {
        bb.pos(x0, y0).tex(u0, v0).colour(colour).endVertex();
        bb.pos(x1, y0).tex(u1, v0).colour(colour).endVertex();
        bb.pos(x0, y1).tex(u0, v1).colour(colour).endVertex();
        bb.pos(x1, y1).tex(u1, v1).colour(colour).endVertex();
    }

    public static void loadQuad(SimpleBufferBuilder bb, float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1) {
        bb.pos(x0, y0).tex(u0, v0).endVertex();
        bb.pos(x1, y0).tex(u1, v0).endVertex();
        bb.pos(x0, y1).tex(u0, v1).endVertex();
        bb.pos(x1, y1).tex(u1, v1).endVertex();
    }

    public enum SpriteFillDirection {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP
    }
}
