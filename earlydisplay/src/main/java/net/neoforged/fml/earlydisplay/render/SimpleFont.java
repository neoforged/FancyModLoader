/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL32C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL32C.GL_RED;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32C.glGenTextures;
import static org.lwjgl.opengl.GL32C.glTexImage2D;
import static org.lwjgl.opengl.GL32C.glTexParameteri;
import static org.lwjgl.stb.STBTruetype.stbtt_GetPackedQuad;
import static org.lwjgl.stb.STBTruetype.stbtt_GetScaledFontVMetrics;
import static org.lwjgl.stb.STBTruetype.stbtt_InitFont;
import static org.lwjgl.stb.STBTruetype.stbtt_PackBegin;
import static org.lwjgl.stb.STBTruetype.stbtt_PackEnd;
import static org.lwjgl.stb.STBTruetype.stbtt_PackFontRanges;
import static org.lwjgl.stb.STBTruetype.stbtt_PackSetOversampling;
import static org.lwjgl.stb.STBTruetype.stbtt_PackSetSkipMissingCodepoints;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import net.neoforged.fml.earlydisplay.theme.ThemeResource;
import net.neoforged.fml.earlydisplay.util.Size;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;

public class SimpleFont implements AutoCloseable {
    private static final int ASCII_GLYPH_COUNT = 127 - 32;

    private int textureId;
    private final int lineSpacing;
    private final int descent;
    private final IntFunction<Glyph> glyphGetter;

    public Size measureText(CharSequence text) {
        var width = 0f;
        var height = 0f;
        var x = 0f;
        var y = 0f;

        var codePoints = text.codePoints().iterator();
        while (codePoints.hasNext()) {
            int codePoint = codePoints.nextInt();
            switch (codePoint) {
                case '\n' -> {
                    width = Math.max(width, x);
                    x = 0;
                    y += lineSpacing();
                }
                case '\t' -> x += getSpaceGlyph().charwidth() * 4;
                default -> {
                    Glyph glyph = getGlyph(codePoint);
                    if (glyph != null) {
                        x += glyph.charwidth();
                    }
                }
            }
        }
        width = Math.max(width, x);
        height = y + descent();
        // Ignore the last line if it was empty
        if (x > 0) {
            height += lineSpacing;
        }

        return new Size(width, height);
    }

    @Override
    public void close() {
        if (textureId != 0) {
            GL32C.glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    public record Glyph(char c, int charwidth, int[] pos, float[] uv) {
        Pos loadQuad(Pos pos, int colour, SimpleBufferBuilder bb) {
            var x0 = pos.x() + pos()[0];
            var y0 = pos.y() + pos()[1];
            var x1 = pos.x() + pos()[2];
            var y1 = pos.y() + pos()[3];
            bb.pos(x0, y0).tex(uv()[0], uv()[1]).colour(colour).endVertex();
            bb.pos(x1, y0).tex(uv()[2], uv()[1]).colour(colour).endVertex();
            bb.pos(x0, y1).tex(uv()[0], uv()[3]).colour(colour).endVertex();
            bb.pos(x1, y1).tex(uv()[2], uv()[3]).colour(colour).endVertex();
            return new Pos(pos.x() + charwidth(), pos.y(), pos.minx());
        }
    }

    public SimpleFont(int lineSpacing, int descent, int textureId, IntFunction<Glyph> glyphGetter) {
        this.lineSpacing = lineSpacing;
        this.descent = descent;
        this.textureId = textureId;
        this.glyphGetter = glyphGetter;
    }

    /**
     * Build the font and store it in the textureNumber location
     */
    public SimpleFont(ThemeResource resource, @Nullable Path externalThemeDirectory) throws IOException {
        try (var nativeBuffer = resource.toNativeBuffer(externalThemeDirectory)) {
            var buf = nativeBuffer.buffer();
            var info = STBTTFontinfo.create();
            if (!stbtt_InitFont(info, buf)) {
                throw new IllegalStateException("Bad font");
            }

            var ascent = new float[1];
            var descent = new float[1];
            var lineGap = new float[1];
            int fontSize = 24;
            stbtt_GetScaledFontVMetrics(buf, 0, fontSize, ascent, descent, lineGap);
            this.lineSpacing = (int) (ascent[0] - descent[0] + lineGap[0]);
            this.descent = (int) Math.floor(descent[0]);
            this.textureId = glGenTextures();
            GlState.activeTexture(GL_TEXTURE0);
            GlState.bindTexture2D(this.textureId);
            GlDebug.labelTexture(this.textureId, "font texture " + resource);
            try (var packedchars = STBTTPackedchar.malloc(ASCII_GLYPH_COUNT)) {
                int texwidth = 256;
                int texheight = 128;
                try (STBTTPackRange.Buffer packRanges = STBTTPackRange.malloc(1)) {
                    var bitmap = BufferUtils.createByteBuffer(texwidth * texheight);
                    try (STBTTPackRange packRange = STBTTPackRange.malloc()) {
                        packRanges.put(packRange.set(fontSize, 32, null, ASCII_GLYPH_COUNT, packedchars, (byte) 1, (byte) 1));
                        packRanges.flip();
                    }

                    try (STBTTPackContext pc = STBTTPackContext.malloc()) {
                        stbtt_PackBegin(pc, bitmap, texwidth, texheight, 0, 1, NULL);
                        stbtt_PackSetOversampling(pc, 1, 1);
                        stbtt_PackSetSkipMissingCodepoints(pc, true);
                        stbtt_PackFontRanges(pc, buf, 0, packRanges);
                        stbtt_PackEnd(pc);
                        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, texwidth, texheight, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                    }
                }
                try (var q = STBTTAlignedQuad.malloc()) {
                    float[] x = new float[1];
                    float[] y = new float[1];
                    Glyph[] glyphs = new Glyph[ASCII_GLYPH_COUNT];

                    for (int i = 0; i < ASCII_GLYPH_COUNT; i++) {
                        x[0] = 0f;
                        y[0] = fontSize;
                        stbtt_GetPackedQuad(packedchars, texwidth, texheight, i, x, y, q, true);
                        glyphs[i] = new Glyph((char) (i + 32), (int) (x[0] - 0f), new int[] { (int) q.x0(), (int) q.y0(), (int) q.x1(), (int) q.y1() }, new float[] { q.s0(), q.t0(), q.s1(), q.t1() });
                    }

                    this.glyphGetter = codepoint -> {
                        if (codepoint < ' ' || codepoint - ' ' > ASCII_GLYPH_COUNT) {
                            return null;
                        }
                        return glyphs[codepoint - ' '];
                    };
                }
            }
        }
    }

    @Nullable
    private Glyph getGlyph(int codepoint) {
        return glyphGetter.apply(codepoint);
    }

    private Glyph getSpaceGlyph() {
        return Objects.requireNonNull(getGlyph(' '));
    }

    public int lineSpacing() {
        return lineSpacing;
    }

    int textureId() {
        return textureId;
    }

    public int descent() {
        return descent;
    }

    public int stringWidth(String text) {
        return stringWidth(text, 0, text.length());
    }

    public int stringWidth(String text, int start, int end) {
        int len = 0;
        for (int i = start; i < end; i++) {
            int c = text.codePointAt(i);
            len += switch (c) {
                case '\n', '\t' -> 0;
                case ' ' -> getSpaceGlyph().charwidth();
                default -> {
                    Glyph glyph = getGlyph(c);
                    yield glyph != null ? glyph.charwidth() : 0;
                }
            };
        }
        return len;
    }

    private record Pos(float x, float y, float minx) {}

    /**
     * A piece of text to display
     *
     * @param string The text
     * @param colour The colour of the text as an RGBA packed int
     */
    public record DisplayText(String string, int colour) {
        Pos generateStringArray(SimpleFont font, Pos pos, SimpleBufferBuilder bb) {
            for (int i = 0; i < string.length(); i++) {
                int codepoint = string.codePointAt(i);
                pos = switch (codepoint) {
                    case '\n' -> new Pos(pos.minx(), pos.y() + font.lineSpacing(), pos.minx());
                    case '\t' -> new Pos(pos.x() + font.getSpaceGlyph().charwidth() * 4, pos.y(), pos.minx());
                    case ' ' -> new Pos(pos.x() + font.getSpaceGlyph().charwidth(), pos.y(), pos.minx());
                    default -> {
                        Glyph glyph = font.getGlyph(codepoint);
                        if (glyph != null) {
                            pos = glyph.loadQuad(pos, colour(), bb);
                        }
                        yield pos;
                    }
                };
            }
            return pos;
        }

        public List<DisplayText> splitAt(int pos, boolean offsetSecond) {
            DisplayText partOne = new DisplayText(string.substring(0, pos), colour);
            DisplayText partTwo = new DisplayText(string.substring(pos + (offsetSecond ? 1 : 0)), colour);
            return List.of(partOne, partTwo);
        }
    }

    /**
     * Generate vertices for a set of display texts
     *
     * @param x     The starting screen x coordinate
     * @param y     The starting screen y coordinate
     * @param texts Some {@link DisplayText} to display
     * @return a {@link SimpleBufferBuilder} that can draw the texts
     */
    public SimpleBufferBuilder generateVerticesForTexts(float x, float y, SimpleBufferBuilder textBB, Iterable<DisplayText> texts) {
        var pos = new Pos(x, y, x);
        for (var text : texts) {
            pos = text.generateStringArray(this, pos, textBB);
        }
        return textBB;
    }
}
