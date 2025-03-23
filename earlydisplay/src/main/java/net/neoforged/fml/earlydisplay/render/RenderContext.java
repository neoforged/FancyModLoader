package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.loading.progress.ProgressMeter;

public record RenderContext(
        SimpleBufferBuilder sharedBuffer,
        Map<String, SimpleFont> fonts,
        Map<String, ElementShader> shaders,
        float availableWidth,
        float availableHeight,
        int animationFrame) {

    public ElementShader bindShader(String shaderId) {
        var shader = shaders.get(shaderId);
        if (shader == null) {
            throw new IllegalArgumentException("Missing shader definition in theme for " + shaderId);
        }
        shader.activate();
        return shader;
    }

    public void blitTexture(Texture texture, float x, float y, float width, float height, int color) {
        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(texture.textureId());

        var shader = bindShader(Theme.SHADER_GUI);
        shader.setUniform1i(ElementShader.UNIFORM_SAMPLER0, 0);

        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);

        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (texture.animationMetadata() != null) {
            int frameCount = texture.animationMetadata().frameCount();
            var frameHeight = texture.physicalHeight() / frameCount;
            var vUnit = frameHeight / (float) texture.physicalHeight();
            v0 = (animationFrame % frameCount) * vUnit;
            v1 = (animationFrame % frameCount + 1) * vUnit;
        }

        QuadHelper.loadQuad(sharedBuffer, x, x + width, y, y + height, u0, u1, v0, v1, color);

        sharedBuffer.draw();
    }

    public SimpleFont getFont(String fontId) {
        var font = fonts.getOrDefault(fontId, fonts.get(Theme.FONT_DEFAULT));
        if (font == null) {
            throw new IllegalStateException("Theme does not contain a default font. Available fonts: " + fonts.keySet());
        }
        return font;
    }

    public void renderText(float x, float y, SimpleFont font, List<SimpleFont.DisplayText> texts) {
        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(font.textureId());
        var shader = bindShader(Theme.SHADER_FONT);
        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        font.generateVerticesForTexts(x, y, sharedBuffer, texts);
        sharedBuffer.draw();
    }

    public void renderProgressBar(Bounds bounds, int cnt, float alpha, SimpleFont font, ProgressMeter pm, ThemeColor background, ThemeColor foreground) {
        if (pm.steps() == 0) {
            progressBar(background, foreground, bounds, alpha, frame -> indeterminateBar(frame, cnt == 0));
        } else {
            progressBar(background, foreground, bounds, alpha, f -> new float[] { 0f, pm.progress() });
        }
    }
    interface ColourFunction {
        int colour(int frame);
    }

    interface ProgressDisplay {
        float[] progress(int frame);
    }

    interface BarPosition {
        int[] location();
    }

    public void progressBar(ThemeColor background, ThemeColor foreground, Bounds bounds, float alpha, ProgressDisplay progressDisplay) {
        bindShader(Theme.SHADER_COLOR);
        var progress = progressDisplay.progress(animationFrame);
        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        var inset = 2;
        var x0 = bounds.left();
        var x1 = bounds.right() + 4 * inset;
        var y0 = bounds.top();
        var y1 = bounds.bottom();
        QuadHelper.loadQuad(sharedBuffer, x0, x1, y0, y1, 0f, 0f, 0f, 0f, foreground.withAlpha(alpha).toArgb());

        x0 += inset;
        x1 -= inset;
        y0 += inset;
        y1 -= inset;
        QuadHelper.loadQuad(sharedBuffer, x0, x1, y0, y1, 0f, 0f, 0f, 0f, background.toArgb());

        x1 = bounds.left() + inset + (int) (progress[1] * bounds.width());
        x0 += inset + progress[0] * bounds.width();
        y0 += inset;
        y1 -= inset;
        QuadHelper.loadQuad(sharedBuffer, x0, x1, y0, y1, 0f, 0f, 0f, 0f, -1 /* TODO */);
        sharedBuffer.draw();
    }

    private static float[] indeterminateBar(int frame, boolean isActive) {
        if (!isActive) {
            return new float[] { 0f, 1f };
        } else {
            var progress = frame % 100;
            return new float[] { Math.clamp((progress - 2) / 100f, 0f, 1f), Math.clamp((progress + 2) / 100f, 0f, 1f) };
        }
    }
}
