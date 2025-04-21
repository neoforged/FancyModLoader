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
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glGetString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.neoforged.fml.earlydisplay.render.elements.ImageElement;
import net.neoforged.fml.earlydisplay.render.elements.LabelElement;
import net.neoforged.fml.earlydisplay.render.elements.PerformanceElement;
import net.neoforged.fml.earlydisplay.render.elements.ProgressBarsElement;
import net.neoforged.fml.earlydisplay.render.elements.RenderElement;
import net.neoforged.fml.earlydisplay.render.elements.StartupLogElement;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import org.lwjgl.opengl.GL32C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadingScreenRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadingScreenRenderer.class);

    private final long glfwWindow;
    private final MaterializedTheme theme;
    private final String mcVersion;
    private final String neoForgeVersion;

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window - note animation is capped at 20FPS via the tickTimer

    private int animationFrame;
    private long nextFrameTime = 0;

    private final EarlyFramebuffer framebuffer;

    private final Semaphore renderLock = new Semaphore(1);

    // Scheduled background rendering of the loading screen
    private final ScheduledFuture<?> automaticRendering;

    private final List<RenderElement> elements;

    private final SimpleBufferBuilder buffer = new SimpleBufferBuilder("shared", 8192);

    /**
     * Render initialization methods called by the Render Thread.
     * It compiles the fragment and vertex shaders for rendering text with STB, and sets up basic render framework.
     * <p>
     * Nothing fancy, we just want to draw and render text.
     */
    public LoadingScreenRenderer(ScheduledExecutorService scheduler,
            long glfwWindow,
            Theme theme,
            String mcVersion,
            String neoForgeVersion) {
        this.glfwWindow = glfwWindow;
        this.mcVersion = mcVersion;
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
        this.theme = MaterializedTheme.materialize(theme);
        this.elements = loadElements();

        // we always render to an 854x480 texture and then fit that to the screen
        framebuffer = new EarlyFramebuffer(854, 480);

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
        if (!loadingScreen.performance().visibility()) {
            elements.add(new PerformanceElement(loadingScreen.performance(), theme));
        }
        if (!loadingScreen.startupLog().visibility()) {
            elements.add(new StartupLogElement(loadingScreen.startupLog(), theme));
        }
        if (!loadingScreen.progressBars().visibility()) {
            elements.add(new ProgressBarsElement(loadingScreen.progressBars(), theme));
        }

        // Add decorative elements
        for (var element : theme.theme().decoration()) {
            elements.add(loadElement(element));
        }

        return elements;
    }

    private RenderElement loadElement(ThemeElement element) {
        return switch (element) {
            case ThemeImageElement imageElement -> new ImageElement(imageElement, theme);

            case ThemeLabelElement labelElement -> new LabelElement(
                    labelElement,
                    theme,
                    Map.of(
                            "version", mcVersion + "-" + neoForgeVersion.split("-")[0]));

            default -> throw new IllegalStateException("Unexpected theme element " + element + " of type " + element.getClass());
        };
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

            framebuffer.activate();
            GlState.viewport(0, 0, framebuffer.width(), framebuffer.height());
            renderToFramebuffer();
            framebuffer.deactivate();

            int[] w = new int[1];
            int[] h = new int[1];
            glfwGetFramebufferSize(glfwWindow, w, h);

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
        }
    }

    public void renderToFramebuffer() {
        GlDebug.pushGroup("update EarlyDisplay framebuffer");
        GlState.readFromOpenGL();
        var backup = GlState.createSnapshot();

        GlState.viewport(0, 0, framebuffer.width(), framebuffer.height());
        framebuffer.activate();

        // Clear the screen to our color
        var background = theme.theme().colorScheme().screenBackground();
        GlState.clearColor(background.r(), background.g(), background.b(), 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        for (var shader : theme.shaders().values()) {
            shader.activate();
            if (shader.hasUniform(ElementShader.UNIFORM_SCREEN_SIZE)) {
                shader.setUniform2f(ElementShader.UNIFORM_SCREEN_SIZE, framebuffer.width(), framebuffer.height());
            }
        }

        var context = new RenderContext(buffer, theme, framebuffer.width(), framebuffer.height(), animationFrame);

        for (var element : this.elements) {
            element.render(context);
        }

        framebuffer.deactivate();

        GlState.applySnapshot(backup);
        GlDebug.popGroup();
    }

    @Override
    public void close() {
        // TODO       this.context.elementShader().close();
        SimpleBufferBuilder.destroy();
    }

    public int getFramebufferTextureId() {
        return framebuffer.getTexture();
    }
}
