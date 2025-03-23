package net.neoforged.fml.earlydisplay.render.elements;

import java.util.List;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.earlydisplay.util.Size;

public class LabelElement extends RenderElement {
    private final String text;

    public LabelElement(String id, String text) {
        super(id);
        this.text = text;
    }

    @Override
    public void render(RenderContext context) {
        var texts = List.of(
                new SimpleFont.DisplayText(text, -1));

        var font = context.getFont(Theme.FONT_DEFAULT);
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
