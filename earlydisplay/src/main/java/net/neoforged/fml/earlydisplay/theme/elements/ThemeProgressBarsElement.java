package net.neoforged.fml.earlydisplay.theme.elements;

import net.neoforged.fml.earlydisplay.theme.ThemeTexture;

public class ThemeProgressBarsElement extends ThemeElement {
    /**
     * The background image being rendered as the base for a progress bar.
     */
    private ThemeTexture background;
    /**
     * The image that will be rendered on top of the background for progress bars that are being filled normally
     * from the left.
     */
    private ThemeTexture foreground;
    /**
     * The image that will be rendered on top of the background for progress bars that are actively animating
     * as an indeterminate progress bar.
     */
    private ThemeTexture foregroundIndeterminate;

    /**
     * The gap in virtual pixels between a bars label and the bar itself.
     */
    private int labelGap;

    /**
     * The gap in virtual pixels between a bar and the next label or bar.
     */
    private int barGap;

    /**
     * Makes the indeterminate progress bars bounce back and forth instead of trying to
     * emulate an infinite scroll, which doesn't work that well with more complex progress bars.
     */
    private boolean indeterminateBounce;

    public ThemeTexture background() {
        return background;
    }

    public void setBackground(ThemeTexture background) {
        this.background = background;
    }

    public ThemeTexture foreground() {
        return foreground;
    }

    public void setForeground(ThemeTexture foreground) {
        this.foreground = foreground;
    }

    public ThemeTexture foregroundIndeterminate() {
        return foregroundIndeterminate;
    }

    public void setForegroundIndeterminate(ThemeTexture foregroundIndeterminate) {
        this.foregroundIndeterminate = foregroundIndeterminate;
    }

    public int labelGap() {
        return labelGap;
    }

    public void setLabelGap(int labelGap) {
        this.labelGap = labelGap;
    }

    public int barGap() {
        return barGap;
    }

    public void setBarGap(int barGap) {
        this.barGap = barGap;
    }

    public boolean indeterminateBounce() {
        return indeterminateBounce;
    }

    public void setIndeterminateBounce(boolean indeterminateBounce) {
        this.indeterminateBounce = indeterminateBounce;
    }
}
