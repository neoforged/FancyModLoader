/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.error;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.AbstractEarlyScreen;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.i18n.FMLTranslations;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

final class ErrorDisplayWindow extends AbstractEarlyScreen {
    private static final int DISPLAY_WIDTH = 854;
    private static final int DISPLAY_HEIGHT = 480;
    private static final int BUTTON_WIDTH = 320;
    private static final int BUTTON_HEIGHT = 40;
    private static final int LIST_BORDER_HEIGHT = 2;
    private static final int SCROLLER_WIDTH = 15;
    private static final int SCROLLER_HEIGHT = 60;
    private static final int ENTRY_PADDING = 10;
    private static final int HEADER_Y = 10;
    private static final int HEADER_LINE_HEIGHT = 18;
    private static final int LEFT_BTN_X = DISPLAY_WIDTH / 2 - 10 - BUTTON_WIDTH;
    private static final int RIGHT_BTN_X = DISPLAY_WIDTH / 2 + 10;
    private static final int TOP_BTN_Y = DISPLAY_HEIGHT - 92;
    private static final int BOTTOM_BTN_Y = DISPLAY_HEIGHT - 47;
    private static final int LIST_Y_TOP = 70;
    private static final int LIST_Y_BOTTOM = DISPLAY_HEIGHT - 100;
    private static final int LIST_CONTENT_Y_TOP = 74;
    private static final int LIST_CONTENT_Y_BOTTOM = DISPLAY_HEIGHT - 102;
    private static final int LIST_BORDER_TOP_Y2 = LIST_Y_TOP - LIST_BORDER_HEIGHT;
    private static final int LIST_BORDER_TOP_Y1 = LIST_BORDER_TOP_Y2 - LIST_BORDER_HEIGHT;
    private static final int LIST_BORDER_BOTTOM_Y1 = LIST_Y_BOTTOM;
    private static final int LIST_BORDER_BOTTOM_Y2 = LIST_BORDER_BOTTOM_Y1 + LIST_BORDER_HEIGHT;
    private static final int LIST_HEIGHT = LIST_Y_BOTTOM - LIST_Y_TOP;
    private static final int LIST_CONTENT_HEIGHT = LIST_CONTENT_Y_BOTTOM - LIST_CONTENT_Y_TOP;
    private static final int LIST_ENTRY_X = 30;
    private static final int LIST_CONTENT_WIDTH = DISPLAY_WIDTH - (LIST_ENTRY_X * 2);
    private static final int SCROLL_SPEED = 10;
    private static final ThemeColor CLEAR_COLOR = ThemeColor.ofArgb(0xFF000000);

    private final SimpleFont font;
    private final int errorLineHeight;
    final Texture buttonTexture;
    final Texture buttonTextureHover;
    final Texture buttonTextureInactive;
    private final List<Button> buttons;
    private final List<HeaderLine> headerTextLines;
    private final List<MessageEntry> entries;
    private final int totalEntryHeight;
    private boolean closed = false;
    private double mouseX = -1;
    private double mouseY = -1;
    private float scrollOffset = 0;
    private boolean draggingScrollbar = false;

    ErrorDisplayWindow(
            ELSRenderBackend backend,
            @Nullable String assetsDir,
            @Nullable String assetIndex,
            List<ModLoadingIssue> issues,
            @Nullable Path modsFolder,
            @Nullable Path logFile,
            @Nullable Path crashReportFile) {
        super("FML Error Screen", () -> backend, Theme.createDefaultTheme(), null, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        SimpleFont mcFont = FontLoader.loadVanillaFont(backend, assetsDir, assetIndex);
        this.font = mcFont != null ? mcFont : theme.getFont(Theme.FONT_DEFAULT);
        this.errorLineHeight = font.lineSpacing() - 5;
        this.buttonTexture = Button.loadTexture(backend, true, false);
        this.buttonTextureHover = Button.loadTexture(backend, true, true);
        this.buttonTextureInactive = Button.loadTexture(backend, false, false);
        boolean translate = mcFont != null;
        BiFunction<String, Object[], String> translator = translate ? FMLTranslations::parseMessage : FMLTranslations::parseEnglishMessage;
        FileOpener opener = FileOpener.get();
        String btnModsText = translator.apply("fml.button.open.mods.folder", new Object[0]);
        String btnReportText = translator.apply("fml.button.open.crashreport", new Object[0]);
        String btnLogText = translator.apply("fml.button.open.log", new Object[0]);
        String btnQuitText = translator.apply("fml.button.quit", new Object[0]);
        this.buttons = List.of(
                new Button(this, LEFT_BTN_X, TOP_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnModsText, modsFolder != null, () -> opener.open(modsFolder)),
                new Button(this, LEFT_BTN_X, BOTTOM_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnReportText, crashReportFile != null, () -> opener.open(crashReportFile)),
                new Button(this, RIGHT_BTN_X, TOP_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnLogText, logFile != null, () -> opener.open(logFile)),
                new Button(this, RIGHT_BTN_X, BOTTOM_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnQuitText, true, () -> closed = true));

        List<ModLoadingIssue> warningEntries = issues.stream()
                .filter(issue -> issue.severity() != ModLoadingIssue.Severity.ERROR)
                .toList();
        List<ModLoadingIssue> errorEntries = issues.stream()
                .filter(issue -> issue.severity() == ModLoadingIssue.Severity.ERROR)
                .toList();
        String errorHeaderText = translator.apply("fml.loadingerrorscreen.errorheader", new Object[] { errorEntries.size() });
        String warningHeaderText = translator.apply("fml.loadingerrorscreen.warningheader", new Object[] { warningEntries.size() });
        // Show errors first, then warnings.
        boolean needSeparators = !warningEntries.isEmpty() && !errorEntries.isEmpty();
        Function<ModLoadingIssue, String> issueTranslator = translate ? FMLTranslations::translateIssue : FMLTranslations::translateIssueEnglish;
        this.entries = new ArrayList<>(errorEntries.size() + warningEntries.size());
        if (needSeparators) {
            this.entries.add(MessageEntry.of(errorHeaderText, font, 0xFFFF5555, true));
        }
        translateEntries(errorEntries, this.entries, font, issueTranslator);
        if (needSeparators) {
            this.entries.add(MessageEntry.of(warningHeaderText, font, 0xFFFFFF55, true));
        }
        translateEntries(warningEntries, this.entries, font, issueTranslator);
        int entryContentHeight = entries.stream().mapToInt(MessageEntry::lineCount).sum() * errorLineHeight;
        this.totalEntryHeight = entryContentHeight + entries.size() * ENTRY_PADDING;

        String headerText;
        // Prioritize showing errors in the header
        int headerTextColor;
        if (!errorEntries.isEmpty()) {
            headerText = errorHeaderText;
            headerTextColor = 0xFFFF5555;
        } else {
            headerText = warningHeaderText;
            headerTextColor = 0xFFFFFF55;
        }
        this.headerTextLines = HeaderLine.of(headerText, font, headerTextColor);
    }

    private static void translateEntries(List<ModLoadingIssue> issues, List<MessageEntry> entries, SimpleFont font, Function<ModLoadingIssue, String> translator) {
        issues.stream().map(translator).map(text -> MessageEntry.of(text, font)).forEach(entries::add);
    }

    void render() {
        this.renderToFramebuffer(CLEAR_COLOR);
    }

    @Override
    protected void renderToFramebuffer(RenderContext ctx) {
        // Background
        ctx.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, 0xFF402020, 0xFF501010);
        // Top edge
        ctx.fillRect(0, LIST_BORDER_TOP_Y1, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0x33FFFFFF);
        ctx.fillRect(0, LIST_BORDER_TOP_Y2, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0xBF000000);
        // Bottom edge
        ctx.fillRect(0, LIST_BORDER_BOTTOM_Y1, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0xBF000000);
        ctx.fillRect(0, LIST_BORDER_BOTTOM_Y2, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0x33FFFFFF);
        // List background
        ctx.fillRect(0, LIST_Y_TOP, DISPLAY_WIDTH, LIST_HEIGHT, 0x70000000);

        for (int i = 0; i < headerTextLines.size(); i++) {
            HeaderLine line = headerTextLines.get(i);
            float x = DISPLAY_WIDTH / 2F - line.width / 2F;
            float y = HEADER_Y + (HEADER_LINE_HEIGHT * i);
            ctx.renderTextWithShadow(x, y, font, line.parts);
        }

        ctx.enableScissor(0, LIST_CONTENT_Y_TOP, DISPLAY_WIDTH, LIST_CONTENT_HEIGHT);
        float y = LIST_Y_TOP - scrollOffset;
        for (MessageEntry entry : entries) {
            float entryHeight = errorLineHeight * entry.lineCount();
            if (y + entryHeight < LIST_Y_TOP) {
                y += entryHeight + ENTRY_PADDING;
                continue;
            } else if (y > LIST_CONTENT_Y_BOTTOM) {
                break;
            }

            for (List<SimpleFont.DisplayText> line : entry.lines()) {
                float textX;
                if (entry.centered) {
                    int width = line.stream()
                            .map(SimpleFont.DisplayText::string)
                            .mapToInt(font::stringWidth)
                            .sum();
                    textX = (DISPLAY_WIDTH / 2F) - (width / 2F);
                } else {
                    textX = LIST_ENTRY_X;
                }
                ctx.renderText(textX, y, font, line);
                y += errorLineHeight;
            }
            y += ENTRY_PADDING;
        }
        ctx.collector().disableScissor();

        if (totalEntryHeight > LIST_CONTENT_HEIGHT) {
            float scrollFactor = scrollOffset / (totalEntryHeight - LIST_CONTENT_HEIGHT - 1);
            float scrollerY = LIST_Y_TOP + scrollFactor * (LIST_HEIGHT - 1 - SCROLLER_HEIGHT);
            ctx.fillRect(DISPLAY_WIDTH - SCROLLER_WIDTH, scrollerY, SCROLLER_WIDTH, SCROLLER_HEIGHT, 0xFFAAAAAA);
        }

        buttons.forEach(btn -> btn.render(ctx, font, mouseX, mouseY));
    }

    private void dragScrollbar(double mouseY) {
        double maxOff = totalEntryHeight - LIST_CONTENT_HEIGHT;
        double offset = (mouseY - LIST_Y_TOP - (SCROLLER_HEIGHT / 2F)) / (LIST_CONTENT_HEIGHT - SCROLLER_HEIGHT);
        scrollOffset = (float) Math.clamp(offset * maxOff, 0, maxOff);
    }

    private void scroll(double delta) {
        float offY = (float) (delta * SCROLL_SPEED);
        scrollOffset = Math.clamp(scrollOffset + offY, 0, Math.max(totalEntryHeight - LIST_CONTENT_HEIGHT, 0));
    }

    void handleCursorPos(long ignoredWindow, double mouseX, double mouseY) {
        this.mouseX = (mouseX - offsetX) / scale;
        this.mouseY = (mouseY - offsetY) / scale;
        if (draggingScrollbar) {
            dragScrollbar(this.mouseY);
        }
    }

    void handleMouseScroll(long ignoredWindow, double ignoredDeltaX, double deltaY) {
        scroll(-deltaY);
    }

    void handleMouseButton(long ignoredWindow, int button, int action, int ignoredMods) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return;

        boolean press = action == GLFW.GLFW_PRESS;
        if (press) {
            buttons.forEach(Button::unfocus);
            for (Button btn : buttons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.press();
                    break;
                }
            }
        }

        if (totalEntryHeight > LIST_CONTENT_HEIGHT) {
            if (press && mouseX > DISPLAY_WIDTH - SCROLLER_WIDTH && mouseY > LIST_Y_TOP && mouseY <= LIST_Y_BOTTOM) {
                draggingScrollbar = true;
                dragScrollbar(mouseY);
            } else if (!press) {
                draggingScrollbar = false;
            }
        }
    }

    void handleKey(long ignoredWindow, int key, int ignoredScancode, int action, int ignoredMods) {
        if (action == GLFW.GLFW_RELEASE) return;

        boolean repeat = action == GLFW.GLFW_REPEAT;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (!repeat) {
                    closed = true;
                }
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_UP -> scroll(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_DOWN -> scroll(1);
            case GLFW.GLFW_KEY_TAB -> {
                if (repeat) break;

                boolean modified = false;
                for (int i = 0; i < buttons.size(); i++) {
                    Button button = buttons.get(i);
                    if (button.isFocused()) {
                        button.unfocus();
                        focusFirstActiveKeyAfter(i);
                        modified = true;
                        break;
                    }
                }
                if (!modified) {
                    focusFirstActiveKeyAfter(-1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (repeat) break;

                for (Button button : buttons) {
                    if (button.isFocused()) {
                        button.press();
                        break;
                    }
                }
            }
        }
    }

    private void focusFirstActiveKeyAfter(int prevIdx) {
        for (int i = 1; i <= buttons.size(); i++) {
            Button newButton = buttons.get((prevIdx + i) % buttons.size());
            if (newButton.isActive()) {
                newButton.focus();
                return;
            }
        }
    }

    void handleClose(long ignoredWindow) {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close(boolean destroyBackend) {
        buttonTexture.close();
        buttonTextureHover.close();
        buttonTextureInactive.close();
        super.close(destroyBackend);
    }

    private record HeaderLine(List<SimpleFont.DisplayText> parts, int width) {
        static List<HeaderLine> of(String text, SimpleFont font, int defaultColor) {
            List<HeaderLine> headerLines = new ArrayList<>();
            for (List<SimpleFont.DisplayText> line : FormatHelper.formatText(text, font, defaultColor, -1)) {
                int width = 0;
                for (SimpleFont.DisplayText part : line) {
                    width += font.stringWidth(part.string());
                }
                headerLines.add(new HeaderLine(line, width));
            }
            return headerLines;
        }
    }

    private record MessageEntry(List<List<SimpleFont.DisplayText>> lines, int lineCount, boolean centered) {
        static MessageEntry of(String text, SimpleFont font) {
            return of(text, font, 0xFFFFFFFF, false);
        }

        static MessageEntry of(String text, SimpleFont font, int defaultColor, boolean centered) {
            List<List<SimpleFont.DisplayText>> lines = FormatHelper.formatText(text, font, defaultColor, LIST_CONTENT_WIDTH);
            return new MessageEntry(lines, lines.size(), centered);
        }
    }
}
