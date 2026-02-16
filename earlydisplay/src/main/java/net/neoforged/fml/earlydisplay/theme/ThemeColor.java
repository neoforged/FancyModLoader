/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.awt.Color;

public record ThemeColor(float r, float g, float b, float a) {

    public static final ThemeColor WHITE = new ThemeColor(1, 1, 1, 1);
    public static ThemeColor ofBytes(int r, int g, int b, int a) {
        return new ThemeColor(r / 255.f, g / 255.f, b / 255.f, a / 255.f);
    }

    public static ThemeColor ofBytes(int r, int g, int b) {
        return ofBytes(r, g, b, 255);
    }

    public static ThemeColor ofArgb(int color) {
        var aByte = (color >> 24) & 0xFF;
        var rByte = (color >> 16) & 0xFF;
        var gByte = (color >> 8) & 0xFF;
        var bByte = (color) & 0xFF;
        return ofBytes(rByte, gByte, bByte, aByte);
    }

    public static ThemeColor ofRgb(int color) {
        var rByte = (color >> 16) & 0xFF;
        var gByte = (color >> 8) & 0xFF;
        var bByte = (color) & 0xFF;
        return ofBytes(rByte, gByte, bByte);
    }

    public ThemeColor withAlpha(float alpha) {
        return new ThemeColor(r, g, b, alpha);
    }

    public int toArgb() {
        return (((int) (a * 255)) & 0xFF) << 24
                | (((int) (r * 255)) & 0xFF) << 16
                | (((int) (g * 255)) & 0xFF) << 8
                | (((int) (b * 255)) & 0xFF);
    }

    public int rByte() {
        return (int) (r * 255);
    }

    public int gByte() {
        return (int) (g * 255);
    }

    public int bByte() {
        return (int) (b * 255);
    }

    public int aByte() {
        return (int) (a * 255);
    }

    public static ThemeColor lerp(ThemeColor a, ThemeColor b, float f) {
        var hsbA = a.toHsb();
        var hsbB = b.toHsb();

        return ofHsb(
                hsbA[0] + (hsbB[0] - hsbA[0]) * f,
                hsbA[1] + (hsbB[1] - hsbA[1]) * f,
                hsbA[2] + (hsbB[2] - hsbA[2]) * f);
    }

    public float[] toHsb() {
        return Color.RGBtoHSB(rByte(), gByte(), bByte(), null);
    }

    public static ThemeColor ofHsb(float h, float s, float b) {
        return ofRgb(Color.HSBtoRGB(h, s, Math.clamp(b, 0, 1)));
    }

    public static ThemeColor scale(ThemeColor color, float scale) {
        return new ThemeColor(
                Math.clamp(color.r() * scale, 0, 1F),
                Math.clamp(color.g() * scale, 0, 1F),
                Math.clamp(color.b() * scale, 0, 1F),
                color.a());
    }
}
