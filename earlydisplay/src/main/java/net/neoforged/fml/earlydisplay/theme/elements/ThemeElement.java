package net.neoforged.fml.earlydisplay.theme.elements;

import java.util.Objects;
import net.neoforged.fml.earlydisplay.util.StyleLength;

public abstract class ThemeElement {
    private String id;

    private boolean maintainAspectRatio = true;
    private StyleLength left = StyleLength.ofUndefined();
    private StyleLength top = StyleLength.ofUndefined();
    private StyleLength right = StyleLength.ofUndefined();
    private StyleLength bottom = StyleLength.ofUndefined();

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    @Override
    public String toString() {
        return id;
    }
}
