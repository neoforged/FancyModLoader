package net.neoforged.fml.earlydisplay.theme.elements;

import net.neoforged.fml.earlydisplay.util.StyleLength;

public abstract class ThemeElement {
    private final String id;

    private StyleLength left = StyleLength.ofUndefined();
    private StyleLength top = StyleLength.ofUndefined();
    private StyleLength right = StyleLength.ofUndefined();
    private StyleLength bottom = StyleLength.ofUndefined();

    public ThemeElement(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public StyleLength left() {
        return left;
    }

    public void setLeft(StyleLength left) {
        this.left = left;
    }

    public StyleLength top() {
        return top;
    }

    public void setTop(StyleLength top) {
        this.top = top;
    }

    public StyleLength right() {
        return right;
    }

    public void setRight(StyleLength right) {
        this.right = right;
    }

    public StyleLength bottom() {
        return bottom;
    }

    public void setBottom(StyleLength bottom) {
        this.bottom = bottom;
    }

    @Override
    public String toString() {
        return id;
    }
}
