/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.util;

import java.util.function.Function;

public class ColorConversion {
    private ColorConversion() {}

    public static float[] RGBtoHSB(int R, int G, int B) {
        float r = R / 255f;
        float g = G / 255f;
        float b = B / 255f;

        float max = Math.max(Math.max(r, g), b);
        float min = Math.min(Math.min(r, g), b);
        float range = max - min;
        float mid = (max + min) / 2;

        float hue = 0;
        if (range == 0) {
            hue = 0;
        } else if (max == r) {
            hue = 60 * (((g - b) / range) % 6);
        } else if (max == g) {
            hue = 60 * ((b - r) / range + 2);
        } else if (max == b) {
            hue = 60 * ((r - g) / range + 4);
        }

        float saturation;
        if (mid == 0 || mid == 1) {
            saturation = 0;
        } else {
            saturation = range / (max + min);
        }
        return new float[] { hue / 360f, saturation, max };
    }

    public static int HSBtoRGB(float H, float S, float B) {
        float h = H % 360;
        float s = Math.clamp(S, 0f, 1f);
        float v = Math.clamp(B, 0f, 1f);

        float rk = (5 + h / 60) % 6;
        float r = v - v * s * Math.max(0, Math.min(Math.min(rk, 4 - rk), 1));
        float gk = (3 + h / 60) % 6;
        float g = v - v * s * Math.max(0, Math.min(Math.min(gk, 4 - gk), 1));
        float bk = (1 + h / 60) % 6;
        float b = v - v * s * Math.max(0, Math.min(Math.min(bk, 4 - bk), 1));
        return (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }
}
