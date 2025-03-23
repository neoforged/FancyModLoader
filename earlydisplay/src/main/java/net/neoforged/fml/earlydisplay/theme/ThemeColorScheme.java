package net.neoforged.fml.earlydisplay.theme;

public class ThemeColorScheme {
    public static final ThemeColorScheme DEFAULT = new ThemeColorScheme(
            new UnresolvedThemeColor(ThemeColor.ofBytes(239, 50, 61), ThemeColor.ofBytes(0, 0, 0)),
            new UnresolvedThemeColor(ThemeColor.ofBytes(255, 255, 255)));

    private final UnresolvedThemeColor background;

    private final UnresolvedThemeColor text;

    private boolean darkMode;

    public ThemeColorScheme(UnresolvedThemeColor background,
            UnresolvedThemeColor text) {
        this.background = background;
        this.text = text;
    }

    public boolean darkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public ThemeColor background() {
        return background.resolve(darkMode);
    }

    public ThemeColor text() {
        return text.resolve(darkMode);
    }
}
