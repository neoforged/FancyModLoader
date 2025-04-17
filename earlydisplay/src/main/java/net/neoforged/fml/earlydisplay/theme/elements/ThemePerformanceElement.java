package net.neoforged.fml.earlydisplay.theme.elements;

import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.theme.ThemeTexture;

public class ThemePerformanceElement extends ThemeElement {
    /**
     * The background image being rendered as the base for a progress bar.
     */
    private ThemeTexture barBackground;
    /**
     * The image that will be rendered on top of the background for progress bars that are being filled normally
     * from the left.
     */
    private ThemeTexture barForeground;
    /**
     * The color to use for coloring the bar when resource usage is low.
     * The actual color will be interpolated between this and {@code highColor}.
     */
    private ThemeColor lowColor;
    /**
     * The color to use for coloring the bar when resource usage is high.
     * The actual color will be interpolated between this and {@code highColor}.
     */
    private ThemeColor highColor;

    public ThemeTexture barBackground() {
        return barBackground;
    }

    public void setBarBackground(ThemeTexture barBackground) {
        this.barBackground = barBackground;
    }

    public ThemeTexture barForeground() {
        return barForeground;
    }

    public void setBarForeground(ThemeTexture barForeground) {
        this.barForeground = barForeground;
    }

    public ThemeColor lowColor() {
        return lowColor;
    }

    public void setLowColor(ThemeColor lowColor) {
        this.lowColor = lowColor;
    }

    public ThemeColor highColor() {
        return highColor;
    }

    public void setHighColor(ThemeColor highColor) {
        this.highColor = highColor;
    }
}
