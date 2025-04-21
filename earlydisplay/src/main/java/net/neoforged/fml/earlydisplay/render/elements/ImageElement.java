/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.elements;

import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;

public class ImageElement extends RenderElement {
    private final Texture texture;

    public ImageElement(ThemeImageElement element, MaterializedTheme theme) {
        super(element, theme);
        this.texture = Texture.create(element.texture());
    }

    @Override
    public void render(RenderContext context) {
        int color = -1;
        // April-fools handling
        if ("squir".equals(id())) {
            int fade = (int) (Math.cos(context.animationFrame() * Math.PI / 16) * 16) + 16;
            color = (fade & 0xff) << 24 | 0xffffff;
        }

        var bounds = resolveBounds(context.availableWidth(), context.availableHeight(), texture.width(), texture.height());

        context.blitTexture(texture, bounds, color);
    }

    @Override
    public void close() {
        this.texture.close();
    }
}
