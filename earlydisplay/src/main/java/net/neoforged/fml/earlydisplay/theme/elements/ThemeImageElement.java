package net.neoforged.fml.earlydisplay.theme.elements;

import net.neoforged.fml.earlydisplay.theme.ThemeTexture;

public class ThemeImageElement extends ThemeElement {
    private final ThemeTexture texture;

    public ThemeImageElement(String id, ThemeTexture texture) {
        super(id);
        this.texture = texture;
    }

    public ThemeTexture texture() {
        return texture;
    }
}
