package net.neoforged.fml.earlydisplay.theme.elements;

import net.neoforged.fml.earlydisplay.theme.ThemeTexture;

public class ThemeImageElement extends ThemeElement {
    private ThemeTexture texture;

    public ThemeTexture texture() {
        return texture;
    }

    public void setTexture(ThemeTexture texture) {
        this.texture = texture;
    }
}
