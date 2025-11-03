/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.error;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import org.jetbrains.annotations.Nullable;

final class FormatHelper {
    static List<List<SimpleFont.DisplayText>> formatText(String text, SimpleFont font, int defaultColor, int maxWidth) {
        List<List<SimpleFont.DisplayText>> lines = new ArrayList<>();
        List<SimpleFont.DisplayText> parts = new ArrayList<>();
        int lastIndex = 0;
        int lastColor = defaultColor;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                parts.add(part(text, lastIndex, i, lastColor));

                lastIndex = i + 2;
                lastColor = getColor(text.charAt(i + 1), lastColor, defaultColor);
            } else if (c == '\n') {
                parts.add(part(text, lastIndex, i, lastColor));
                lines.add(parts);
                parts = new ArrayList<>();

                lastIndex = i + 1;
            }
        }
        if (lastIndex < text.length() - 1) {
            parts.add(part(text, lastIndex, text.length(), lastColor));
        }
        if (!parts.isEmpty()) {
            lines.add(parts);
        }
        if (maxWidth > -1) {
            wrapLines(lines, font, maxWidth);
        }
        return lines;
    }

    private static SimpleFont.DisplayText part(String text, int start, int end, int color) {
        return new SimpleFont.DisplayText(text.substring(start, end), color);
    }

    private static int getColor(char code, int lastColor, int defaultColor) {
        return switch (code) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            case 'r' -> defaultColor;
            default -> lastColor;
        };
    }

    private static void wrapLines(List<List<SimpleFont.DisplayText>> lines, SimpleFont font, int maxWidth) {
        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            List<SimpleFont.DisplayText> parts = lines.get(lineIdx);
            int lineWidth = 0;
            int[] widthsBeforePart = new int[parts.size() + 1];
            for (int partIdx = 0; partIdx < parts.size(); partIdx++) {
                SimpleFont.DisplayText part = parts.get(partIdx);
                widthsBeforePart[partIdx] = lineWidth;
                lineWidth += font.stringWidth(part.string());
                if (lineWidth < maxWidth) {
                    continue;
                }

                SplitPos pos = findSplitPos(parts, font, partIdx, widthsBeforePart, maxWidth);
                if (pos == null) {
                    // We somehow failed to find a suitable wrapping index and have to accept the text getting cut off
                    break;
                }

                List<SimpleFont.DisplayText> newPartsOne = new ArrayList<>();
                List<SimpleFont.DisplayText> newPartsTwo = new ArrayList<>();
                for (int copyPartIdx = 0; copyPartIdx < parts.size(); copyPartIdx++) {
                    if (copyPartIdx < pos.partIdx) {
                        newPartsOne.add(parts.get(copyPartIdx));
                    } else if (copyPartIdx == pos.partIdx) {
                        List<SimpleFont.DisplayText> splitParts = parts.get(copyPartIdx).splitAt(pos.charIdx, pos.onSpace);
                        newPartsOne.add(splitParts.getFirst());
                        newPartsTwo.add(splitParts.getLast());
                    } else {
                        newPartsTwo.add(parts.get(copyPartIdx));
                    }
                }
                lines.set(lineIdx, newPartsOne);
                lines.add(lineIdx + 1, newPartsTwo);
                break;
            }
        }
    }

    @Nullable
    private static SplitPos findSplitPos(List<SimpleFont.DisplayText> parts, SimpleFont font, int startPart, int[] widthsBeforePart, int maxWidth) {
        for (int partIdx = startPart; partIdx >= 0; partIdx--) {
            String text = parts.get(partIdx).string();
            for (int charIdx = text.length() - 1; charIdx >= 0; charIdx--) {
                if (text.charAt(charIdx) != ' ') {
                    continue;
                }

                if (widthsBeforePart[partIdx] + font.stringWidth(text, 0, charIdx) <= maxWidth) {
                    return new SplitPos(partIdx, charIdx, true);
                }
            }
        }
        for (int partIdx = startPart; partIdx >= 0; partIdx--) {
            String text = parts.get(partIdx).string();
            for (int charIdx = text.length() - 1; charIdx >= 0; charIdx--) {
                if (widthsBeforePart[partIdx] + font.stringWidth(text, 0, charIdx + 1) <= maxWidth) {
                    return new SplitPos(partIdx, charIdx, false);
                }
            }
        }
        return null;
    }

    private record SplitPos(int partIdx, int charIdx, boolean onSpace) {}

    private FormatHelper() {}
}
