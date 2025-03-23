package net.neoforged.fml.earlydisplay.theme.elements;

public class ThemeLabelElement extends ThemeElement {
    private final String text;

    public ThemeLabelElement(String id, String text) {
        super(id);
        this.text = text;
    }

    public String text() {
        return text;
    }
}
