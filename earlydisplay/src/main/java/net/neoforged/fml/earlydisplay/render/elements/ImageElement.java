package net.neoforged.fml.earlydisplay.render.elements;

import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.Texture;

public class ImageElement extends RenderElement {
    private final Texture texture;

    public ImageElement(String id, MaterializedTheme theme, Texture texture) {
        super(id, theme);
        this.texture = texture;
    }

    @Override
    public void render(RenderContext context) {
        int color = -1;
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
