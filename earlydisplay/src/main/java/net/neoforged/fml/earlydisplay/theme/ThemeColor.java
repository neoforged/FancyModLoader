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
        return (int) (r * 256);
    }

    public int gByte() {
        return (int) (g * 256);
    }

    public int bByte() {
        return (int) (b * 256);
    }

    public int aByte() {
        return (int) (a * 256);
    }

    public static int hsvToRGB(float hue, float saturation, float value) {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (float) i;
        float f1 = value * (1.0F - saturation);
        float f2 = value * (1.0F - f * saturation);
        float f3 = value * (1.0F - (1.0F - f) * saturation);
        float f4;
        float f5;
        float f6;
        switch (i) {
            case 0:
                f4 = value;
                f5 = f3;
                f6 = f1;
                break;
            case 1:
                f4 = f2;
                f5 = value;
                f6 = f1;
                break;
            case 2:
                f4 = f1;
                f5 = value;
                f6 = f3;
                break;
            case 3:
                f4 = f1;
                f5 = f2;
                f6 = value;
                break;
            case 4:
                f4 = f3;
                f5 = f1;
                f6 = value;
                break;
            case 5:
                f4 = value;
                f5 = f1;
                f6 = f2;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }

        int j = Math.clamp((int) (f4 * 255.0F), 0, 255);
        int k = Math.clamp((int) (f5 * 255.0F), 0, 255);
        int l = Math.clamp((int) (f6 * 255.0F), 0, 255);
        return 0xFF << 24 | j << 16 | k << 8 | l;
    }

    public float[] toHsb() {
        return Color.RGBtoHSB(rByte(), gByte(), bByte(), null);
    }

    public static ThemeColor ofHsb(float h, float s, float b) {
        return ofRgb(Color.HSBtoRGB(h, s, Math.clamp(b, 0, 1)));
    }
}
