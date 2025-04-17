package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;

import java.util.List;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.util.Bounds;

public record RenderContext(
        SimpleBufferBuilder sharedBuffer,
        MaterializedTheme theme,
        float availableWidth,
        float availableHeight,
        int animationFrame) {
    public ElementShader bindShader(String shaderId) {
        var shader = theme.getShader(shaderId);
        shader.activate();
        return shader;
    }

    public void blitTexture(Texture texture, Bounds bounds) {
        blitTexture(texture, bounds, -1);
    }

    public void blitTexture(Texture texture, Bounds bounds, int color) {
        blitTexture(texture, bounds.left(), bounds.top(), bounds.width(), bounds.height(), color);
    }

    public void blitTexture(Texture texture, float x, float y, float width, float height) {
        blitTexture(texture, x, y, width, height, -1);
    }

    public void blitTexture(Texture texture, float x, float y, float width, float height, int color) {
        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(texture.textureId());

        var shader = bindShader(Theme.SHADER_GUI);
        shader.setUniform1i(ElementShader.UNIFORM_SAMPLER0, 0);

        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);

        QuadHelper.fillSprite(
                sharedBuffer,
                texture,
                x,
                y,
                0,
                width,
                height,
                color,
                QuadHelper.SpriteFillDirection.TOP_TO_BOTTOM,
                animationFrame);

        sharedBuffer.draw();
    }

    public void renderText(float x, float y, SimpleFont font, List<SimpleFont.DisplayText> texts) {
        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(font.textureId());
        bindShader(Theme.SHADER_FONT);
        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        font.generateVerticesForTexts(x, y, sharedBuffer, texts);
        sharedBuffer.draw();
    }
}
