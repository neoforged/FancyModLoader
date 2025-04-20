/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.elements;

import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.earlydisplay.util.StyleLength;

public abstract class RenderElement implements AutoCloseable {
    private final String id;
    protected final MaterializedTheme theme;

    private boolean maintainAspectRatio;
    private StyleLength left;
    private StyleLength top;
    private StyleLength right;
    private StyleLength bottom;
    protected SimpleFont font;

    public RenderElement(ThemeElement element, MaterializedTheme theme) {
        this.theme = theme;
        this.id = element.id();
        this.font = theme.getFont(element.font());
        this.left = element.left();
        this.top = element.top();
        this.right = element.right();
        this.bottom = element.bottom();
        this.maintainAspectRatio = element.maintainAspectRatio();
    }

    public String id() {
        return this.id;
    }

    public abstract void render(RenderContext context);

    public Bounds resolveBounds(float availableWidth, float availableHeight, float intrinsicWidth, float intrinsicHeight) {
        var left = resolve(this.left, availableWidth);
        var right = availableWidth - resolve(this.right, availableWidth);
        var top = resolve(this.top, availableHeight);
        var bottom = availableHeight - resolve(this.bottom, availableHeight);

        var width = right - left;
        var height = bottom - top;

        boolean widthDefined = !Float.isNaN(width);
        boolean heightDefined = !Float.isNaN(height);

        // Handle aspect ratio
        if (widthDefined != heightDefined) {
            if (maintainAspectRatio) {
                float ar = intrinsicWidth / intrinsicHeight;
                if (widthDefined) {
                    height = width / ar;
                } else {
                    width = height * ar;
                }
            } else if (widthDefined) {
                height = intrinsicHeight;
            } else if (heightDefined) {
                width = intrinsicWidth;
            }
        } else if (!widthDefined && !heightDefined) {
            width = intrinsicWidth;
            height = intrinsicHeight;
        }

        // Fill out the unspecified size based on width/height
        if (Float.isNaN(left) && Float.isNaN(right)) {
            left = 0;
            right = width;
        } else if (Float.isNaN(right)) {
            right = left + width;
        } else if (Float.isNaN(left)) {
            left = right - width;
        }
        if (Float.isNaN(top) && Float.isNaN(bottom)) {
            top = 0;
            bottom = height;
        } else if (Float.isNaN(bottom)) {
            bottom = top + height;
        } else if (Float.isNaN(top)) {
            top = bottom - height;
        }

        return new Bounds(left, top, right, bottom);
    }

    private float resolve(StyleLength length, float availableSpace) {
        return switch (length.unit()) {
            case UNDEFINED -> Float.NaN;
            case POINT -> length.value();
            case REM -> length.value() * font.lineSpacing();
            case PERCENT -> (length.value() * availableSpace) / 100.0f;
        };
    }

    public StyleLength left() {
        return left;
    }

    public void setLeft(StyleLength left) {
        this.left = left;
    }

    public StyleLength top() {
        return top;
    }

    public void setTop(StyleLength top) {
        this.top = top;
    }

    public StyleLength right() {
        return right;
    }

    public void setRight(StyleLength right) {
        this.right = right;
    }

    public StyleLength bottom() {
        return bottom;
    }

    public void setBottom(StyleLength bottom) {
        this.bottom = bottom;
    }

    public boolean maintainAspectRatio() {
        return maintainAspectRatio;
    }

    public void setMaintainAspectRatio(boolean maintainAspectRatio) {
        this.maintainAspectRatio = maintainAspectRatio;
    }

    //
//    public void retire(final int frame) {
//        this.retireCount = frame;
//    }
//
//    public static RenderElement mojang(final int textureId, final int frameStart) {
//        return new RenderElement("mojang logo", () -> (bb, ctx, frame) -> {
//            var size = 256 * ctx.scale();
//            var x0 = (ctx.scaledWidth() - 2 * size) / 2;
//            var y0 = 64 * ctx.scale() + 32;
//            ctx.elementShader().updateTextureUniform(0);
//            ctx.elementShader().updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
//            var fade = Math.min((frame - frameStart) * 10, 255);
//            GlState.bindTexture2D(textureId);
//            bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
//            QuadHelper.loadQuad(bb, x0, x0 + size, y0, y0 + size / 2f, 0f, 1f, 0f, 0.5f, ctx.colourScheme.foreground().packedint(fade));
//            QuadHelper.loadQuad(bb, x0 + size, x0 + 2 * size, y0, y0 + size / 2f, 0f, 1f, 0.5f, 1f, ctx.colourScheme.foreground().packedint(fade));
//            bb.draw();
//            GlState.bindTexture2D(0);
//        });
//    }
//
//    public static RenderElement forgeVersionOverlay(SimpleFont font, String version) {
//        return new RenderElement("version overlay", RenderElement.initializeText(font, (bb, fnt, ctx) -> font.generateVerticesForTexts(ctx.scaledWidth() - font.stringWidth(version) - 10,
//                ctx.scaledHeight() - font.lineSpacing() + font.descent() - 10, bb,
//                new SimpleFont.DisplayText(version, ctx.colourScheme.foreground().packedint(RenderElement.globalAlpha)))));
//    }
//
//    public static RenderElement squir() {
//        return new RenderElement("squir", RenderElement.initializeTexture(SQUIR, (bb, context, size, frame) -> {
//
//        }));
//    }
//
//    public static RenderElement fox(SimpleFont font) {
//        return new RenderElement("fox", RenderElement.initializeTexture(FOX_RUNNING, (bb, context, size, frame) -> {
//            int framecount = 28;
//            float aspect = size[0] * (float) framecount / size[1];
//            int outsize = size[0];
//            int offset = outsize / 6;
//            var x0 = context.scaledWidth() - outsize * context.scale() + offset;
//            var x1 = context.scaledWidth() + offset;
//            var y0 = context.scaledHeight() - outsize * context.scale() / aspect - font.descent() - font.lineSpacing();
//            var y1 = context.scaledHeight() - font.descent() - font.lineSpacing();
//            int frameidx = frame % framecount;
//            float framesize = 1 / (float) framecount;
//            float framepos = frameidx * framesize;
//            QuadHelper.loadQuad(bb, x0, x1, y0, y1, 0f, 1f, framepos, framepos + framesize, globalAlpha << 24 | 0xFFFFFF);
//        }));
//    }
//
//    public static RenderElement progressBars(SimpleFont font) {
//        return new RenderElement("progress bars", () -> (bb, ctx, frame) -> RenderElement.startupProgressBars(font, bb, ctx, frame));
//    }
//
//    public static RenderElement performanceBar(SimpleFont font) {
//        return new RenderElement("performance bar", () -> (bb, ctx, frame) -> RenderElement.memoryInfo(font, bb, ctx, frame));
//    }
//
//    public static void startupProgressBars(SimpleFont font, final SimpleBufferBuilder buffer, final DisplayContext context, final int frameNumber) {
//        Renderer acc = null;
//        var barCount = 2;
//        List<ProgressMeter> currentProgress = StartupNotificationManager.getCurrentProgress();
//        var size = currentProgress.size();
//        var alpha = 0xFF;
//        for (int i = 0; i < barCount && i < size; i++) {
//            final ProgressMeter pm = currentProgress.get(i);
//            Renderer barRenderer = barRenderer(i, alpha, font, pm, context);
//            acc = barRenderer.then(acc);
//        }
//        if (acc != null)
//            acc.accept(buffer, context, frameNumber);
//    }
//
//    private static void memoryInfo(SimpleFont font, final SimpleBufferBuilder buffer, final DisplayContext context, final int frameNumber) {
//        var y = 10 * context.scale();
//        PerformanceInfo pi = context.performance();
//        final int colour = hsvToRGB((1.0f - (float) Math.pow(pi.memory(), 1.5f)) / 3f, 1.0f, 0.5f);
//        var bar = progressBar(ctx -> new int[]{(ctx.scaledWidth() - BAR_WIDTH * ctx.scale()) / 2, y, BAR_WIDTH * ctx.scale()}, f -> colour, f -> new float[]{0f, pi.memory()});
//        var width = font.stringWidth(pi.text());
//        Renderer label = (bb, ctx, frame) -> renderText(font, text(ctx.scaledWidth() / 2 - width / 2, y + 18, pi.text(), context.colourScheme.foreground().packedint(globalAlpha)), bb, ctx);
//        bar.then(label).accept(buffer, context, frameNumber);
//    }
//
//    private static Initializer initializeText(SimpleFont font, TextGenerator textGenerator) {
//        return () -> (bb, context, frame) -> renderText(font, textGenerator, bb, context);
//    }
//
//    private static void renderText(final SimpleFont font, final TextGenerator textGenerator, final SimpleBufferBuilder bb, final DisplayContext context) {
//        GlState.activeTexture(GL_TEXTURE0);
//        GlState.bindTexture2D(font.textureId());
//        context.elementShader().updateRenderTypeUniform(ElementShader.RenderType.FONT);
//        bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
//        textGenerator.accept(bb, font, context);
//        bb.draw();
//    }
//
//    private static TextGenerator text(int x, int y, String text, int colour) {
//        return (bb, font, context) -> font.generateVerticesForTexts(x, y, bb, new SimpleFont.DisplayText(text, colour));
//    }

    public static float clamp(float num, float min, float max) {
        if (num < min) {
            return min;
        } else {
            return Math.min(num, max);
        }
    }

    public static int clamp(int num, int min, int max) {
        if (num < min) {
            return min;
        } else {
            return Math.min(num, max);
        }
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return id;
    }
}
