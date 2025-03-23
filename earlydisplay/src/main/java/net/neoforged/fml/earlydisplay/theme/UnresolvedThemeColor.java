package net.neoforged.fml.earlydisplay.theme;

import org.jetbrains.annotations.Nullable;

public record UnresolvedThemeColor(ThemeColor lightBackground, @Nullable ThemeColor darkBackground) {
    public UnresolvedThemeColor(ThemeColor lightBackground) {
        this(lightBackground, null);
    }

    public ThemeColor resolve(boolean darkMode) {
        return darkMode && darkBackground != null ? darkBackground : lightBackground;
    }
}
