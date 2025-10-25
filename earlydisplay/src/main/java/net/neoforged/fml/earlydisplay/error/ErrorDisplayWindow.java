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
import net.neoforged.fml.earlydisplay.render.EarlyFramebuffer;
import net.neoforged.fml.earlydisplay.render.ElementShader;
import net.neoforged.fml.earlydisplay.render.GlState;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.i18n.FMLTranslations;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;

final class ErrorDisplayWindow {
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
    private static final int SCROLL_SPEED = 10;

    final long windowHandle;
    private final MaterializedTheme theme;
    private final SimpleFont font;
    private final int errorLineHeight;
    private final EarlyFramebuffer framebuffer;
    private final SimpleBufferBuilder bufferBuilder;
    final Texture buttonTexture;
    final Texture buttonTextureHover;
    private final List<Button> buttons;
    private final List<HeaderLine> headerTextLines;
    private final List<MessageEntry> entries;
    private final int totalEntryHeight;
    private boolean closed = false;
    private int offsetX = 0;
    private int offsetY = 0;
    private float scale = 1F;
    private double mouseX = -1;
    private double mouseY = -1;
    private float scrollOffset = 0;
    private boolean draggingScrollbar = false;

    ErrorDisplayWindow(
            long windowHandle,
            @Nullable String assetsDir,
            @Nullable String assetIndex,
            List<ModLoadingIssue> issues,
            Path modsFolder,
            Path logFile,
            Path crashReportFile) {
        this.windowHandle = windowHandle;
        this.theme = MaterializedTheme.materialize(Theme.createDefaultTheme(), null);
        SimpleFont mcFont = FontLoader.loadVanillaFont(assetsDir, assetIndex);
        this.font = mcFont != null ? mcFont : theme.getFont(Theme.FONT_DEFAULT);
        this.errorLineHeight = font.lineSpacing() - 5;
        this.framebuffer = new EarlyFramebuffer(DISPLAY_WIDTH, DISPLAY_HEIGHT);
        this.bufferBuilder = new SimpleBufferBuilder("shared_error", 8192);
        this.buttonTexture = Button.loadTexture(false);
        this.buttonTextureHover = Button.loadTexture(true);
        boolean translate = mcFont != null;
        BiFunction<String, Object[], String> translator = translate ? FMLTranslations::parseMessage : FMLTranslations::parseEnglishMessage;
        FileOpener opener = FileOpener.get();
        String btnModsText = translator.apply("fml.button.open.mods.folder", new Object[0]);
        String btnReportText = translator.apply("fml.button.open.crashreport", new Object[0]);
        String btnLogText = translator.apply("fml.button.open.log", new Object[0]);
        String btnQuitText = translator.apply("fml.button.quit", new Object[0]);
        this.buttons = List.of(
                new Button(this, LEFT_BTN_X, TOP_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnModsText, () -> opener.open(modsFolder)),
                new Button(this, LEFT_BTN_X, BOTTOM_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnReportText, () -> opener.open(crashReportFile)),
                new Button(this, RIGHT_BTN_X, TOP_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnLogText, () -> opener.open(logFile)),
                new Button(this, RIGHT_BTN_X, BOTTOM_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, btnQuitText, () -> closed = true));

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
            this.entries.add(MessageEntry.of(errorHeaderText, 0xFFFF5555, true));
        }
        translateEntries(errorEntries, this.entries, issueTranslator);
        if (needSeparators) {
            this.entries.add(MessageEntry.of(warningHeaderText, 0xFFFFFF55, true));
        }
        translateEntries(warningEntries, this.entries, issueTranslator);
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

    private static void translateEntries(List<ModLoadingIssue> issues, List<MessageEntry> entries, Function<ModLoadingIssue, String> translator) {
        issues.stream().map(translator).map(MessageEntry::of).forEach(entries::add);
    }

    void render() {
        framebuffer.activate();

        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight);
        framebuffer.resize(fbWidth[0], fbHeight[0]);

        // Fit the layout rectangle into the screen while maintaining aspect ratio
        float desiredAspectRatio = DISPLAY_WIDTH / (float) DISPLAY_HEIGHT;
        float actualAspectRatio = framebuffer.width() / (float) framebuffer.height();
        if (actualAspectRatio > desiredAspectRatio) {
            // This means we are wider than the desired aspect ratio, and have to center horizontally
            float actualWidth = desiredAspectRatio * framebuffer.height();
            offsetX = (int) (framebuffer.width() - actualWidth) / 2;
            offsetY = 0;
            scale = (float) framebuffer.height() / DISPLAY_HEIGHT;
            GlState.viewport(offsetX, 0, (int) actualWidth, framebuffer.height());
        } else {
            // This means we are taller than the desired aspect ratio, and have to center vertically
            float actualHeight = framebuffer.width() / desiredAspectRatio;
            offsetX = 0;
            offsetY = (int) (framebuffer.height() - actualHeight) / 2;
            scale = (float) framebuffer.width() / DISPLAY_WIDTH;
            GlState.viewport(0, offsetY, framebuffer.width(), (int) actualHeight);
        }

        GlState.clearColor(0F, 0F, 0F, 1F);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ZERO, GL11C.GL_ONE);

        RenderContext ctx = new RenderContext(bufferBuilder, theme, DISPLAY_WIDTH, DISPLAY_HEIGHT, offsetX, offsetY, scale, 0);
        for (ElementShader shader : theme.shaders().values()) {
            shader.activate();
            if (shader.hasUniform(ElementShader.UNIFORM_SCREEN_SIZE)) {
                shader.setUniform2f(ElementShader.UNIFORM_SCREEN_SIZE, DISPLAY_WIDTH, DISPLAY_HEIGHT);
            }
        }

        renderToFramebuffer(ctx);

        framebuffer.deactivate();

        GlState.viewport(0, 0, framebuffer.width(), framebuffer.height());
        framebuffer.blitToScreen(theme.theme().colorScheme().screenBackground(), framebuffer.width(), framebuffer.height());
        GLFW.glfwSwapBuffers(windowHandle);
    }

    private void renderToFramebuffer(RenderContext ctx) {
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

        GlState.scissorTest(true);
        ctx.scissorBox(0, LIST_CONTENT_Y_TOP, DISPLAY_WIDTH, LIST_CONTENT_HEIGHT);
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
        GlState.scissorTest(false);

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
                    btn.onPress.run();
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
                        buttons.get((i + 1) % buttons.size()).focus();
                        modified = true;
                        break;
                    }
                }
                if (!modified) {
                    buttons.getFirst().focus();
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (repeat) break;

                for (Button button : buttons) {
                    if (button.isFocused()) {
                        button.onPress.run();
                        break;
                    }
                }
            }
        }
    }

    void handleClose(long ignoredWindow) {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }

    void teardown() {
        theme.close();
        framebuffer.close();
        bufferBuilder.close();
        buttonTexture.close();
        buttonTextureHover.close();
        SimpleBufferBuilder.destroy();
    }

    private record HeaderLine(List<SimpleFont.DisplayText> parts, int width) {
        static List<HeaderLine> of(String text, SimpleFont font, int defaultColor) {
            List<HeaderLine> headerLines = new ArrayList<>();
            for (List<SimpleFont.DisplayText> line : FormatHelper.formatText(text, defaultColor)) {
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
        static MessageEntry of(String text) {
            return of(text, 0xFFFFFFFF, false);
        }

        static MessageEntry of(String text, int defaultColor, boolean centered) {
            List<List<SimpleFont.DisplayText>> lines = FormatHelper.formatText(text, defaultColor);
            return new MessageEntry(lines, lines.size(), centered);
        }
    }
}
