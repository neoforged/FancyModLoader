package net.neoforged.fml.earlydisplay.theme;

public record ThemeColorScheme(ThemeColor background, ThemeColor text) {
    public static final ThemeColorScheme DEFAULT = new ThemeColorScheme(
            ThemeColor.ofBytes(239, 50, 61),
            ThemeColor.ofBytes(255, 255, 255));
}
