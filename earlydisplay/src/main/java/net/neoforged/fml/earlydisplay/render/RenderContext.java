/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.earlydisplay.render.backend.ELSDrawCollector;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderPipeline;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.util.Bounds;

public record RenderContext(
        ELSRenderBackend backend,
        ELSDrawCollector collector,
        SimpleBufferBuilder sharedBuffer,
        MaterializedTheme theme,
        Map<String, ELSRenderPipeline> pipelines,
        float availableWidth,
        float availableHeight,
        int viewportOffsetX,
        int viewportOffsetY,
        float viewportScale,
        int windowHeight,
        int animationFrame) {
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

    public void blitTextureRegion(
            Texture texture,
            float x,
            float y,
            float width,
            float height,
            int color,
            float u0,
            float u1,
            float v0,
            float v1) {
        ELSRenderPipeline pipeline = this.pipelines.get(Theme.SHADER_GUI);
        sharedBuffer.begin(pipeline.vertexFormat(), pipeline.vertexMode());

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

        SimpleBufferBuilder.Result result = this.sharedBuffer.finishAndUpload(this.backend);
        this.collector.submitDraw(pipeline, texture.texture(), result);
    }

    public void renderTextWithShadow(float x, float y, SimpleFont font, List<SimpleFont.DisplayText> texts) {
        List<SimpleFont.DisplayText> shadowTexts = Lists.transform(texts, text -> new SimpleFont.DisplayText(text.string(), ThemeColor.scale(ThemeColor.ofArgb(text.colour()), .25F).toArgb()));
        renderText(x + 2, y + 2, font, shadowTexts);
        renderText(x, y, font, texts);
    }

    public void renderText(float x, float y, SimpleFont font, List<SimpleFont.DisplayText> texts) {
        ELSRenderPipeline pipeline = this.pipelines.get(Theme.SHADER_FONT);
        sharedBuffer.begin(pipeline.vertexFormat(), pipeline.vertexMode());
        font.generateVerticesForTexts(x, y, sharedBuffer, texts);
        SimpleBufferBuilder.Result result = this.sharedBuffer.finishAndUpload(this.backend);
        this.collector.submitDraw(pipeline, font.texture(), result);
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

        this.enableScissor(
                (int) barBounds.left(),
                (int) barBounds.top(),
                (int) (barBounds.width() * fillFactor),
                (int) barBounds.height());
        blitTexture(sprites.progressBarForeground(), barBounds, foregroundColor);
        this.collector.disableScissor();
    }

    public void fillRect(float x, float y, float width, float height, int color) {
        fillRect(x, y, width, height, color, color);
    }

    public void fillRect(float x, float y, float width, float height, int colorTop, int colorBottom) {
        ELSRenderPipeline pipeline = this.pipelines.get(Theme.SHADER_COLOR);
        sharedBuffer.begin(pipeline.vertexFormat(), pipeline.vertexMode());
        sharedBuffer.pos(x, y).tex(0, 0).colour(colorTop).endVertex();
        sharedBuffer.pos(x, y + height).tex(0, 0).colour(colorBottom).endVertex();
        sharedBuffer.pos(x + width, y + height).tex(0, 0).colour(colorBottom).endVertex();
        sharedBuffer.pos(x + width, y).tex(0, 0).colour(colorTop).endVertex();

        SimpleBufferBuilder.Result result = this.sharedBuffer.finishAndUpload(this.backend);
        this.collector.submitDraw(pipeline, null, result);
    }

    public void enableScissor(int x, int y, int width, int height) {
        // glScissor applies to the whole window, not just the viewport set via glViewport
        this.collector.enableScissor(
                (int) (viewportOffsetX + x * viewportScale),
                (int) (windowHeight - viewportOffsetY - (y + height) * viewportScale),
                Math.max(1, (int) (width * viewportScale)),
                Math.max(1, (int) (height * viewportScale)));
    }
}
