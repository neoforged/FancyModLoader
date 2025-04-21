/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme.elements;

import net.neoforged.fml.earlydisplay.theme.ThemeTexture;

public class ThemeImageElement extends ThemeDecorativeElement {
    private ThemeTexture texture;

    public ThemeTexture texture() {
        return texture;
    }

    public void setTexture(ThemeTexture texture) {
        this.texture = texture;
    }
}
