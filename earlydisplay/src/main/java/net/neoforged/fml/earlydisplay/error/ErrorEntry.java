package net.neoforged.fml.earlydisplay.error;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.earlydisplay.render.SimpleFont;

record ErrorEntry(List<List<SimpleFont.DisplayText>> lines, int lineCount) {
    static ErrorEntry of(String text) {
        List<List<SimpleFont.DisplayText>> lines = new ArrayList<>();
        List<SimpleFont.DisplayText> parts = new ArrayList<>();
        int lastIndex = 0;
        int lastColor = 0xFFFFFFFF;
        int lineCount = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                parts.add(part(text, lastIndex, i, lastColor));

                lastIndex = i + 2;
                lastColor = getColor(text.charAt(i + 1), lastColor);
            } else if (c == '\n') {
                parts.add(part(text, lastIndex, i, lastColor));
                lines.add(parts);
                parts = new ArrayList<>();

                lastIndex = i + 1;
                lineCount++;
            }
        }
        if (lastIndex < text.length() - 1) {
            parts.add(part(text, lastIndex, text.length(), lastColor));
        }
        if (!parts.isEmpty()) {
            lines.add(parts);
        }
        return new ErrorEntry(lines, lineCount);
    }

    private static SimpleFont.DisplayText part(String text, int start, int end, int color) {
        return new SimpleFont.DisplayText(text.substring(start, end), color);
    }

    private static int getColor(char code, int lastColor) {
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
            case 'f', 'r' -> 0xFFFFFFFF;
            default -> lastColor;
        };
    }
}
