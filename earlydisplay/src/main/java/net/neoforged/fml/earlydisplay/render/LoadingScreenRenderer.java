/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_RENDERER;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_VENDOR;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL11C.GL_ZERO;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glGetString;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.neoforged.fml.earlydisplay.render.elements.ImageElement;
import net.neoforged.fml.earlydisplay.render.elements.LabelElement;
import net.neoforged.fml.earlydisplay.render.elements.MojangLogoElement;
import net.neoforged.fml.earlydisplay.render.elements.PerformanceElement;
import net.neoforged.fml.earlydisplay.render.elements.ProgressBarsElement;
import net.neoforged.fml.earlydisplay.render.elements.RenderElement;
import net.neoforged.fml.earlydisplay.render.elements.StartupLogElement;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadingScreenRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadingScreenRenderer.class);
    public static final int LAYOUT_WIDTH = 854;
    public static final int LAYOUT_HEIGHT = 480;
    @VisibleForTesting
    public static volatile boolean rendered = false;

    private final long glfwWindow;
    private final MaterializedTheme theme;

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window - note animation is capped at 20FPS via the tickTimer

    private int animationFrame;
    private long nextFrameTime = 0;

    private final EarlyFramebuffer framebuffer;

    private final Semaphore renderLock = new Semaphore(1);

    // Scheduled background rendering of the loading screen
    private final ScheduledFuture<?> automaticRendering;

    private final List<RenderElement> elements;

    private final SimpleBufferBuilder buffer = new SimpleBufferBuilder("shared", 8192);

    private final Supplier<String> minecraftVersion;
    private final Supplier<String> neoForgeVersion;

    /**
     * Render initialization methods called by the Render Thread.
     * It compiles the fragment and vertex shaders for rendering text with STB, and sets up basic render framework.
     * <p>
     * Nothing fancy, we just want to draw and render text.
     */
    public LoadingScreenRenderer(ScheduledExecutorService scheduler,
            long glfwWindow,
            Theme theme,
            @Nullable Path externalThemeDirectory,
            Supplier<String> minecraftVersion,
            Supplier<String> neoForgeVersion) {
        this.glfwWindow = glfwWindow;
        this.minecraftVersion = minecraftVersion;
        this.neoForgeVersion = neoForgeVersion;

        // This thread owns the GL render context now. We should make a note of that.
        glfwMakeContextCurrent(glfwWindow);
        // Wait for one frame to be complete before swapping; enable vsync in other words.
        glfwSwapInterval(1);
        var capabilities = createCapabilities();
        GlState.readFromOpenGL();
        GlDebug.setCapabilities(capabilities);
        LOGGER.info("GL info: {} GL version {}, {}", glGetString(GL_RENDERER), glGetString(GL_VERSION), glGetString(GL_VENDOR));

        // Create GL resources
        this.theme = MaterializedTheme.materialize(theme, externalThemeDirectory);
        this.elements = loadElements();

        // we always render to an 854x480 texture and then fit that to the screen
        framebuffer = new EarlyFramebuffer(LAYOUT_WIDTH, LAYOUT_HEIGHT);

        // Set the clear color based on the colour scheme
        var background = theme.colorScheme().screenBackground();
        GlState.clearColor(background.r(), background.g(), background.b(), 1f);
        GL32C.glClear(GL_COLOR_BUFFER_BIT);

        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glfwMakeContextCurrent(0);
        this.automaticRendering = scheduler.scheduleWithFixedDelay(this::renderToScreen, 50, 50, TimeUnit.MILLISECONDS);
        // schedule a 50 ms ticker to try and smooth out the rendering
        scheduler.scheduleWithFixedDelay(() -> animationFrame++, 1, 50, TimeUnit.MILLISECONDS);
    }

    private List<RenderElement> loadElements() {
        var elements = new ArrayList<RenderElement>();

        var loadingScreen = theme.theme().loadingScreen();
        if (loadingScreen.performance().visible()) {
            elements.add(new PerformanceElement(loadingScreen.performance(), theme));
        }
        if (loadingScreen.startupLog().visible()) {
            elements.add(new StartupLogElement(loadingScreen.startupLog(), theme));
        }
        if (loadingScreen.progressBars().visible()) {
            elements.add(new ProgressBarsElement(loadingScreen.progressBars(), theme));
        }
        if (loadingScreen.mojangLogo().visible()) {
            elements.add(new MojangLogoElement(loadingScreen.mojangLogo(), theme));
        }

        // Add decorative elements
        for (var entry : loadingScreen.decoration().entrySet()) {
            var element = entry.getValue();
            if (!element.visible()) {
                continue; // Likely reconfigured in an extended theme
            }
            elements.add(loadElement(entry.getKey(), element));
        }

        return elements;
    }

    private RenderElement loadElement(String id, ThemeElement element) {
        var renderElement = switch (element) {
            case ThemeImageElement imageElement -> new ImageElement(imageElement, theme);

            case ThemeLabelElement labelElement -> new LabelElement(
                    labelElement,
                    theme,
                    () -> Map.of(
                            "version", getVersionString()));

            default -> throw new IllegalStateException("Unexpected theme element " + element + " of type " + element.getClass());
        };
        renderElement.setId(id);
        return renderElement;
    }

    private String getVersionString() {
        var result = new StringBuilder();
        var minecraftVersion = this.minecraftVersion.get();
        if (minecraftVersion != null) {
            result.append(minecraftVersion);
        }
        var neoForgeVersion = this.neoForgeVersion.get();
        if (neoForgeVersion != null) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(neoForgeVersion.split("-")[0]);
        }
        return result.toString();
    }

    public void stopAutomaticRendering() throws TimeoutException, InterruptedException {
        this.automaticRendering.cancel(false);
        if (!renderLock.tryAcquire(5, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        // we don't want the lock, just making sure it's back on the main thread
        renderLock.release();
    }

    /**
     * The main render loop.
     * renderThread executes this.
     * <p>
     * Performs initialization and then ticks the screen at 20 fps.
     * When the thread is killed, context is destroyed.
     */
    public void renderToScreen() {
        if (!renderLock.tryAcquire()) {
            return;
        }
        try {
            long nt;
            if ((nt = System.nanoTime()) < nextFrameTime) {
                return;
            }
            nextFrameTime = nt + MINFRAMETIME;
            glfwMakeContextCurrent(glfwWindow);

            GlState.readFromOpenGL();
            var backup = GlState.createSnapshot();

            int[] w = new int[1];
            int[] h = new int[1];
            glfwGetFramebufferSize(glfwWindow, w, h);
            framebuffer.resize(w[0], h[0]);

            renderToFramebuffer();

            GlState.viewport(0, 0, w[0], h[0]);
            framebuffer.blitToScreen(this.theme.theme().colorScheme().screenBackground(), w[0], h[0]);
            // Swap buffers; we're done
            glfwSwapBuffers(glfwWindow);

            GlState.applySnapshot(backup);
        } catch (Throwable t) {
            LOGGER.error("Unexpected error while rendering the loading screen", t);
        } finally {
            if (this.automaticRendering != null)
                glfwMakeContextCurrent(0); // we release the gl context IF we're running off the main thread
            renderLock.release();
            rendered = true;
        }
    }

    public void renderToFramebuffer() {
        GlDebug.pushGroup("update EarlyDisplay framebuffer");
        GlState.readFromOpenGL();
        var backup = GlState.createSnapshot();

        framebuffer.activate();

        // Fit the layout rectangle into the screen while maintaining aspect ratio
        var desiredAspectRatio = LAYOUT_WIDTH / (float) LAYOUT_HEIGHT;
        var actualAspectRatio = framebuffer.width() / (float) framebuffer.height();
        if (actualAspectRatio > desiredAspectRatio) {
            // This means we are wider than the desired aspect ratio, and have to center horizontally
            var actualWidth = desiredAspectRatio * framebuffer.height();
            GlState.viewport((int) (framebuffer.width() - actualWidth) / 2, 0, (int) actualWidth, framebuffer.height());
        } else {
            // This means we are taller than the desired aspect ratio, and have to center vertically
            var actualHeight = framebuffer.width() / desiredAspectRatio;
            GlState.viewport(0, (int) (framebuffer.height() - actualHeight) / 2, framebuffer.width(), (int) actualHeight);
        }

        // Clear the screen to our color
        var background = theme.theme().colorScheme().screenBackground();
        GlState.clearColor(background.r(), background.g(), background.b(), 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);

        for (var shader : theme.shaders().values()) {
            shader.activate();
            if (shader.hasUniform(ElementShader.UNIFORM_SCREEN_SIZE)) {
                shader.setUniform2f(ElementShader.UNIFORM_SCREEN_SIZE, LAYOUT_WIDTH, LAYOUT_HEIGHT);
            }
        }

        var context = new RenderContext(buffer, theme, LAYOUT_WIDTH, LAYOUT_HEIGHT, animationFrame);

        for (var element : this.elements) {
            element.render(context);
        }

        framebuffer.deactivate();

        GlState.applySnapshot(backup);
        GlDebug.popGroup();
    }

    @Override
    public void close() {
        var previousContext = GLFW.glfwGetCurrentContext();
        var previousCaps = GL.getCapabilities();

        boolean needsToRestoreContext = false;
        if (previousContext != glfwWindow) {
            GLFW.glfwMakeContextCurrent(glfwWindow);
            GL.createCapabilities();
            needsToRestoreContext = true;
        }

        try {
            theme.close();
            for (var element : elements) {
                element.close();
            }
            framebuffer.close();
            buffer.close();
            SimpleBufferBuilder.destroy();
        } finally {
            if (needsToRestoreContext) {
                GLFW.glfwMakeContextCurrent(previousContext);
                GL.setCapabilities(previousCaps);
            }
        }
    }

    public int getFramebufferTextureId() {
        return framebuffer.getTexture();
    }
}
