/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.elements;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.earlydisplay.util.Placeholders;
import net.neoforged.fml.earlydisplay.util.Size;

public class LabelElement extends RenderElement {
    private final String text;

    public LabelElement(ThemeLabelElement element, MaterializedTheme theme, Map<String, String> placeholders) {
        super(element, theme);
        this.text = Placeholders.resolve(element.text(), placeholders);
    }

    @Override
    public void render(RenderContext context) {
        var texts = List.of(
                new SimpleFont.DisplayText(text, -1));

        var intrinsicSize = getIntrinsicSize(texts, font);
        var bounds = resolveBounds(context.availableWidth(), context.availableHeight(), intrinsicSize.width(), intrinsicSize.height());

        context.renderText(bounds.left(), bounds.top(), font, texts);
    }

    private static Size getIntrinsicSize(List<SimpleFont.DisplayText> texts, SimpleFont font) {
        var bounds = new Bounds(0, 0, 0, 0);
        for (var text : texts) {
            bounds = bounds.union(
                    new Bounds(0, bounds.bottom(), font.measureText(text.string())));
        }
        return new Size(bounds.width(), bounds.height());
    }
}
