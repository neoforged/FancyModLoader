package net.neoforged.fml.earlydisplay.theme.elements;

import java.util.Objects;

public class ThemeLabelElement extends ThemeElement {
    private String text = "";

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text);
    }
}
