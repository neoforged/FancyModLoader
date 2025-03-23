package net.neoforged.fml.earlydisplay.theme;

public record ThemeColor(float r, float g, float b, float a) {
    public static ThemeColor ofBytes(int r, int g, int b, int a) {
        return new ThemeColor(r / 255.f, g / 255.f, b / 255.f, a / 255.f);
    }

    public static ThemeColor ofBytes(int r, int g, int b) {
        return ofBytes(r, g, b, 255);
    }

    public ThemeColor withAlpha(float alpha) {
        return new ThemeColor(r, g, b, alpha);
    }

    public int toArgb() {
        return (((int) (a * 255)) & 0xFF) << 24
                | (((int) (b * 255)) & 0xFF) << 16
                | (((int) (g * 255)) & 0xFF) << 8
                | (((int) (r * 255)) & 0xFF);
    }
}
