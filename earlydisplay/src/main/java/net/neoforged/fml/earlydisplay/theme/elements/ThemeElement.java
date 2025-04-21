/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme.elements;

import java.util.Objects;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.util.StyleLength;

public abstract class ThemeElement {
    private boolean visibility = false;
    private boolean maintainAspectRatio = true;
    private StyleLength left = StyleLength.ofUndefined();
    private StyleLength top = StyleLength.ofUndefined();
    private StyleLength right = StyleLength.ofUndefined();
    private StyleLength bottom = StyleLength.ofUndefined();
    private String font = Theme.FONT_DEFAULT;

    public abstract String id();

    public boolean visibility() {
        return visibility;
    }

    public void setVisibility(boolean visibility) {
        this.visibility = visibility;
    }

    public StyleLength left() {
        return left;
    }

    public void setLeft(StyleLength left) {
        this.left = Objects.requireNonNull(left);
    }

    public StyleLength top() {
        return top;
    }

    public void setTop(StyleLength top) {
        this.top = Objects.requireNonNull(top);
    }

    public StyleLength right() {
        return right;
    }

    public void setRight(StyleLength right) {
        this.right = Objects.requireNonNull(right);
    }

    public StyleLength bottom() {
        return bottom;
    }

    public void setBottom(StyleLength bottom) {
        this.bottom = Objects.requireNonNull(bottom);
    }

    public boolean maintainAspectRatio() {
        return maintainAspectRatio;
    }

    public void setMaintainAspectRatio(boolean maintainAspectRatio) {
        this.maintainAspectRatio = maintainAspectRatio;
    }

    public String font() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    @Override
    public String toString() {
        return id();
    }
}
