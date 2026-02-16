/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme.elements;

public class ThemeProgressBarsElement extends ThemeElement {
    /**
     * The gap in virtual pixels between a bars label and the bar itself.
     */
    private int labelGap;

    /**
     * The gap in virtual pixels between a bar and the next label or bar.
     */
    private int barGap;

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
}
