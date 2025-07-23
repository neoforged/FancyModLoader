package net.neoforged.fml.earlydisplay.error;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.neoforged.fml.earlydisplay.render.GlDebug;
import net.neoforged.fml.earlydisplay.render.GlState;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;

/**
 * Loader for the GNU Unifont font definitions included in vanilla MC
 */
final class FontLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String[] FONT_PATHS = new String[] {
            "minecraft/font/unifont.zip",
            "minecraft/font/unifont_pua.zip",
            "minecraft/font/unifont_jp.zip"
    };
    private static final int GLYPH_HEIGHT = 16;

    @Nullable
    static SimpleFont loadVanillaFont(@Nullable String assetsDir, @Nullable String assetIndex) {
        if (assetsDir == null || assetIndex == null) {
            return null;
        }

        Path assets = Path.of(assetsDir);
        Path index = assets.resolve("indexes").resolve(assetIndex + ".json");
        if (!Files.isRegularFile(index)) {
            return null;
        }

        List<Path> fontZipPaths = new ArrayList<>(FONT_PATHS.length);
        try (BufferedReader reader = new BufferedReader(Files.newBufferedReader(index))) {
            JsonObject objects = new Gson().fromJson(reader, JsonObject.class)
                    .getAsJsonObject("objects");
            for (String path : FONT_PATHS) {
                if (objects.has(path)) {
                    String hash = objects.getAsJsonObject(path).get("hash").getAsString();
                    Path fontZip = assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                    if (Files.isRegularFile(fontZip)) {
                        fontZipPaths.add(fontZip);
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to load asset index file", e);
            return null;
        }

        if (fontZipPaths.isEmpty()) {
            return null;
        }

        List<ProtoGlyph> glyphs = new ArrayList<>();
        for (Path zipPath : fontZipPaths) {
            try (ZipInputStream stream = new ZipInputStream(Files.newInputStream(zipPath))) {
                List<ProtoGlyph> fileGlyphs = new ArrayList<>();
                ZipEntry entry;
                while ((entry = stream.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith(".hex")) {
                        parseHexFile(stream, fileGlyphs::add);
                    }
                }
                glyphs.addAll(fileGlyphs);
            } catch (Throwable e) {
                LOGGER.error("Failed to load font ZIP", e);
            }
        }

        return glyphs.isEmpty() ? null : buildFont(glyphs);
    }

    private static void parseHexFile(InputStream stream, Consumer<ProtoGlyph> glyphOutput) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            int colon = line.indexOf(':');
            if (colon < 4 || colon > 6) {
                throw new IllegalStateException();
            }

            int codepoint = Integer.parseInt(line.substring(0, colon), 16);

            String bitmap = line.substring(colon + 1);
            int hexCount = bitmap.length();
            if (hexCount != 32 && hexCount != 64 && hexCount != 96 && hexCount != 128) {
                throw new IllegalStateException();
            }

            int hexPerLine = hexCount / GLYPH_HEIGHT;
            int width = hexPerLine * 4;
            int[] lines = new int[GLYPH_HEIGHT];
            for (int i = 0; i < hexCount; i++) {
                int lineIdx = i / hexPerLine;
                int offset = (hexPerLine - 1 - (i % hexPerLine)) * 4;
                int digit = parseHexDigit(bitmap.charAt(i));
                lines[lineIdx] |= (digit << offset);
            }

            glyphOutput.accept(new ProtoGlyph(codepoint, width, lines));
        }
    }

    private static int parseHexDigit(char digit) {
        return switch (digit) {
            case '0' -> 0;
            case '1' -> 1;
            case '2' -> 2;
            case '3' -> 3;
            case '4' -> 4;
            case '5' -> 5;
            case '6' -> 6;
            case '7' -> 7;
            case '8' -> 8;
            case '9' -> 9;
            case 'a', 'A' -> 10;
            case 'b', 'B' -> 11;
            case 'c', 'C' -> 12;
            case 'd', 'D' -> 13;
            case 'e', 'E' -> 14;
            case 'f', 'F' -> 15;
            default -> throw new IllegalArgumentException("Not a hex digit: " + digit);
        };
    }

    @Nullable
    private static SimpleFont buildFont(List<ProtoGlyph> glyphs) {
        int textureId = GL11C.glGenTextures();
        GlState.activeTexture(GL13C.GL_TEXTURE0);
        GlState.bindTexture2D(textureId);
        GlDebug.labelTexture(textureId, "unifont texture");

        int totalPixels = glyphs.stream().mapToInt(g -> g.width * GLYPH_HEIGHT).sum();
        boolean incWidth = true;
        int texWidth = 512;
        int texHeight = 512;
        while (texWidth * texHeight < totalPixels) {
            if (incWidth) {
                texWidth <<= 1;
            } else {
                texHeight <<= 1;
            }
            incWidth = !incWidth;
        }
        int maxTexSize = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if (texWidth > maxTexSize || texHeight > maxTexSize) {
            // Abort if the GPU does not support the texture size required to fit the font atlas
            return null;
        }

        int x = 0;
        int y = 0;
        Map<Integer, SimpleFont.Glyph> glyphMap = new HashMap<>();
        ByteBuffer bitmap = BufferUtils.createByteBuffer(texWidth * texHeight);
        for (ProtoGlyph glyph : glyphs) {
            char c = Character.toChars(glyph.codepoint)[0];
            int[] pos = new int[] { 0, 4, glyph.width, GLYPH_HEIGHT + 4 };
            float[] uv = new float[] {
                    (float) x / texWidth,
                    (float) y / texHeight,
                    (float) (x + glyph.width) / texWidth,
                    (float) (y + GLYPH_HEIGHT) / texHeight
            };
            glyphMap.put(glyph.codepoint, new SimpleFont.Glyph(c, glyph.width, pos, uv));

            int[] lines = glyph.lines;
            for (int i = 0; i < lines.length; i++) {
                int line = lines[i];
                int baseIdx = (y + i) * texWidth + x;
                for (int lx = 0; lx < glyph.width; lx++) {
                    int idx = baseIdx + lx;
                    int value = ((line >> (glyph.width - 1 - lx)) & 0x1) * 255;
                    bitmap.put(idx, (byte) value);
                }
            }

            x += glyph.width;
            if (x >= texWidth) {
                x = 0;
                y += GLYPH_HEIGHT;
            }
        }

        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RED, texWidth, texHeight, 0, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, bitmap);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);

        // SimpleFont relies on at least a space character being present
        if (!glyphMap.containsKey((int) ' ')) {
            return null;
        }
        return new SimpleFont(24, GLYPH_HEIGHT, textureId, glyphMap::get);
    }

    private record ProtoGlyph(int codepoint, int width, int[] lines) {}

    private FontLoader() {}
}
