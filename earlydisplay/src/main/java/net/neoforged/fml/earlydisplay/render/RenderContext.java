/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import com.google.common.collect.Lists;
import java.util.List;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.util.Bounds;

public record RenderContext(
        SimpleBufferBuilder sharedBuffer,
        MaterializedTheme theme,
        float availableWidth,
        float availableHeight,
        int viewportOffsetX,
        int viewportOffsetY,
        float viewportScale,
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
        blitTextureRegion(texture, x, y, width, height, color, 0, 1, 0, 1);
    }

    public void blitTextureRegion(Texture texture,
            float x,
            float y,
            float width,
            float height,
            int color,
            float u0,
            float u1,
            float v0,
            float v1) {
        GlState.bindTexture2D(texture.textureId());
        GlState.bindSampler(0);

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
                animationFrame,
                u0,
                u1,
                v0,
                v1);

        sharedBuffer.draw();
    }

    public void renderTextWithShadow(float x, float y, SimpleFont font, List<SimpleFont.DisplayText> texts) {
        List<SimpleFont.DisplayText> shadowTexts = Lists.transform(texts, text -> new SimpleFont.DisplayText(text.string(), ThemeColor.scale(ThemeColor.ofArgb(text.colour()), .25F).toArgb()));
        renderText(x + 2, y + 2, font, shadowTexts);
        renderText(x, y, font, texts);
    }

    public void renderText(float x, float y, SimpleFont font, List<SimpleFont.DisplayText> texts) {
        GlState.bindTexture2D(font.textureId());
        GlState.bindSampler(0);
        bindShader(Theme.SHADER_FONT);
        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        font.generateVerticesForTexts(x, y, sharedBuffer, texts);
        sharedBuffer.draw();
    }

    public void renderIndeterminateProgressBar(Bounds backgroundBounds) {
        var sprites = theme.sprites();

        blitTexture(sprites.progressBarBackground(), backgroundBounds);

        if (theme.theme().sprites().progressBarIndeterminateBounces()) {
            // Indeterminate progress bars are rendered as a 20% piece that travels back and forth
            var barX = 0;
            var barWidth = (int) (backgroundBounds.width() * 0.2f);
            var availableSpace = (int) (backgroundBounds.width() - barWidth);
            if (availableSpace > 0) {
                float f = (animationFrame() % 200) / 100.0f;
                if (f > 1) {
                    f = 1 - (f - 1);
                }
                barX = (int) (f * availableSpace);
            }
            blitTexture(
                    sprites.progressBarIndeterminate(),
                    backgroundBounds.left() + barX,
                    backgroundBounds.top(),
                    barWidth,
                    backgroundBounds.height());
        } else {
            // Indeterminate progress bars are rendered as a 20% piece that's scrolling left-to-right and then resets
            var centerPercentage = (animationFrame() % 120) - 10;
            var start = Math.clamp((centerPercentage - 10) / 100f, 0f, 1f);
            var end = Math.clamp((centerPercentage + 10) / 100f, 0f, 1f);
            blitTexture(
                    sprites.progressBarIndeterminate(),
                    (int) (backgroundBounds.left() + backgroundBounds.width() * start),
                    backgroundBounds.top(),
                    (int) (backgroundBounds.width() * (end - start)),
                    backgroundBounds.height());
        }
    }

    public void renderProgressBar(Bounds barBounds, float fillFactor) {
        renderProgressBar(barBounds, fillFactor, ThemeColor.WHITE.toArgb());
    }

    public void renderProgressBar(Bounds barBounds, float fillFactor, int foregroundColor) {
        fillFactor = Math.clamp(fillFactor, 0, 1);

        var sprites = theme.sprites();

        blitTexture(sprites.progressBarBackground(), barBounds);

        if (fillFactor == 0) {
            return;
        }

        GlState.scissorTest(true);
        scissorBox(
                barBounds.left(),
                barBounds.top(),
                barBounds.width() * fillFactor,
                barBounds.height());
        blitTexture(sprites.progressBarForeground(), barBounds, foregroundColor);
        GlState.scissorTest(false);
    }

    public void fillRect(float x, float y, float width, float height, int color) {
        fillRect(x, y, width, height, color, color);
    }

    public void fillRect(float x, float y, float width, float height, int colorTop, int colorBottom) {
        bindShader("color");
        sharedBuffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        sharedBuffer.pos(x, y).tex(0, 0).colour(colorTop).endVertex();
        sharedBuffer.pos(x + width, y).tex(0, 0).colour(colorTop).endVertex();
        sharedBuffer.pos(x, y + height).tex(0, 0).colour(colorBottom).endVertex();
        sharedBuffer.pos(x + width, y + height).tex(0, 0).colour(colorBottom).endVertex();
        sharedBuffer.draw();
    }

    public void scissorBox(int x, int y, int width, int height) {
        scissorBox((float) x, (float) y, (float) width, (float) height);
    }

    private void scissorBox(float x, float y, float width, float height) {
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("Scissor area must have non-zero width/height");
        }

        // glScissor applies to the whole window, not just the viewport set via glViewport
        int left = (int) Math.floor(viewportOffsetX + x * viewportScale);
        int right = (int) Math.ceil(viewportOffsetX + (x + width) * viewportScale);
        int bottom = (int) Math.floor(viewportOffsetY + y * viewportScale);
        int top = (int) Math.ceil(viewportOffsetY + (y + height) * viewportScale);
        GlState.scissorBox(left, bottom, right - left, top - bottom);
    }
}
