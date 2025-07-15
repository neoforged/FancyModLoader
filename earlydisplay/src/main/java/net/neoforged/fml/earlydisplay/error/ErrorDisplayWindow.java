package net.neoforged.fml.earlydisplay.error;

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
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class ErrorDisplayWindow {
    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window
    private static final int DISPLAY_WIDTH = 854;
    private static final int DISPLAY_HEIGHT = 480;
    private static final int BUTTON_WIDTH = 320;
    private static final int BUTTON_HEIGHT = 40;
    private static final int LIST_BORDER_HEIGHT = 2;
    private static final int SCROLLER_WIDTH = 15;
    private static final int SCROLLER_HEIGHT = 60;
    private static final int ENTRY_PADDING = 10;
    private static final int HEADER_ONE_Y = 10;
    private static final int HEADER_TWO_Y = 28;
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
    private final List<ModLoadingIssue> errors;
    private final MaterializedTheme theme;
    private final SimpleFont font;
    private final int errorLineHeight;
    private final EarlyFramebuffer framebuffer;
    private final SimpleBufferBuilder bufferBuilder;
    final Texture buttonTexture;
    final Texture buttonTextureHover;
    private final List<Button> buttons;
    private final List<ErrorEntry> errorEntries;
    private final int totalEntryHeight;
    private boolean closed = false;
    private long nextFrameTime = 0;
    private int offsetX = 0;
    private int offsetY = 0;
    private float scale = 1F;
    private double mouseX = -1;
    private double mouseY = -1;
    private float scrollOffset = 0;
    private boolean draggingScrollbar = false;

    ErrorDisplayWindow(long windowHandle, List<ModLoadingIssue> errors, Path modsFolder, Path logFile, Path crashReportFile) {
        this.windowHandle = windowHandle;
        this.errors = errors;
        this.theme = MaterializedTheme.materialize(Theme.createDefaultTheme(), null);
        this.font = theme.getFont(Theme.FONT_DEFAULT);
        this.errorLineHeight = font.lineSpacing() - 5;
        this.framebuffer = new EarlyFramebuffer(DISPLAY_WIDTH, DISPLAY_HEIGHT);
        this.bufferBuilder = new SimpleBufferBuilder("shared_error", 8192);
        this.buttonTexture = Button.loadTexture(false);
        this.buttonTextureHover = Button.loadTexture(true);
        FileOpener opener = FileOpener.get();
        this.buttons = List.of(
                new Button(this, LEFT_BTN_X, TOP_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "Open Mods Folder", () -> opener.open(modsFolder)),
                new Button(this, LEFT_BTN_X, BOTTOM_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "Open crash report", () -> opener.open(crashReportFile)),
                new Button(this, RIGHT_BTN_X, TOP_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "Open log file", () -> opener.open(logFile)),
                new Button(this, RIGHT_BTN_X, BOTTOM_BTN_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "Quit game", () -> closed = true)
        );
        this.errorEntries = errors.stream()
                .map(FMLTranslations::translateIssueEnglish)
                .map(ErrorEntry::of)
                .toList();
        int entryContentHeight = errorEntries.stream().mapToInt(ErrorEntry::lineCount).sum() * errorLineHeight;
        this.totalEntryHeight = entryContentHeight + errorEntries.size() * ENTRY_PADDING;
    }

    void render() {
        long nanoTime = System.nanoTime();
        if (nanoTime < nextFrameTime) {
            return;
        }
        nextFrameTime = nanoTime + MINFRAMETIME;

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

        RenderContext ctx = new RenderContext(bufferBuilder, theme, DISPLAY_WIDTH, DISPLAY_HEIGHT, 0);
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
        ctx.fillRect(0,  0, DISPLAY_WIDTH, DISPLAY_HEIGHT, 0xFF402020, 0xFF501010);
        // Top edge
        ctx.fillRect(0, LIST_BORDER_TOP_Y1, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0x33FFFFFF);
        ctx.fillRect(0, LIST_BORDER_TOP_Y2, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0xBF000000);
        // Bottom edge
        ctx.fillRect(0, LIST_BORDER_BOTTOM_Y1, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0xBF000000);
        ctx.fillRect(0, LIST_BORDER_BOTTOM_Y2, DISPLAY_WIDTH, LIST_BORDER_HEIGHT, 0x33FFFFFF);
        // List background
        ctx.fillRect(0, LIST_Y_TOP, DISPLAY_WIDTH, LIST_HEIGHT, 0x70000000);

        String text = "Error loading mods";
        int w = font.stringWidth(text);
        ctx.renderTextWithShadow(DISPLAY_WIDTH / 2F - w / 2F, HEADER_ONE_Y, font, List.of(new SimpleFont.DisplayText(text, 0xFFFF5555)));
        String format = errors.size() == 1 ? "%d error has occurred during loading" : "%d errors have occurred during loading";
        text = String.format(Locale.ROOT, format, errors.size());
        w = font.stringWidth(text);
        ctx.renderTextWithShadow(DISPLAY_WIDTH / 2F - w / 2F, HEADER_TWO_Y, font, List.of(new SimpleFont.DisplayText(text, 0xFFFF5555)));

        GlState.scissorTest(true);
        GlState.scissorBox(offsetX, (int) (offsetY + LIST_CONTENT_Y_TOP * scale), (int) (DISPLAY_WIDTH * scale), (int) (LIST_CONTENT_HEIGHT * scale));
        float y = LIST_Y_TOP - scrollOffset;
        for (ErrorEntry entry : errorEntries) {
            float entryHeight = errorLineHeight * entry.lineCount();
            if (y + entryHeight < LIST_Y_TOP) {
                y += entryHeight + ENTRY_PADDING;
                continue;
            } else if (y > LIST_CONTENT_Y_BOTTOM) {
                break;
            }

            for (List<SimpleFont.DisplayText> line : entry.lines()) {
                ctx.renderText(LIST_ENTRY_X, y, font, line);
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
        scrollOffset = Math.clamp(scrollOffset + offY, 0, totalEntryHeight - LIST_CONTENT_HEIGHT);
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
            for (Button btn : buttons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onPress().run();
                    break;
                }
            }
        }
        if (press && mouseX > DISPLAY_WIDTH - SCROLLER_WIDTH && mouseY > LIST_Y_TOP && mouseY <= LIST_Y_BOTTOM) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
        } else if (!press) {
            draggingScrollbar = false;
        }
    }

    void handleKey(long ignoredWindow, int key, int ignoredScancode, int action, int ignoredMods) {
        if (action == GLFW.GLFW_RELEASE) return;

        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> closed = true;
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_UP -> scroll(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_DOWN -> scroll(1);
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
    }
}
