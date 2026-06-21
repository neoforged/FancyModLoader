/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.neoforged.fml.earlydisplay.AbstractEarlyScreen;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.elements.ImageElement;
import net.neoforged.fml.earlydisplay.render.elements.LabelElement;
import net.neoforged.fml.earlydisplay.render.elements.MojangLogoElement;
import net.neoforged.fml.earlydisplay.render.elements.PerformanceElement;
import net.neoforged.fml.earlydisplay.render.elements.ProgressBarsElement;
import net.neoforged.fml.earlydisplay.render.elements.RenderElement;
import net.neoforged.fml.earlydisplay.render.elements.StartupLogElement;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeLoadingScreen;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeDecorativeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoadingScreenRenderer extends AbstractEarlyScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadingScreenRenderer.class);
    private static final int LAYOUT_WIDTH = 854;
    private static final int LAYOUT_HEIGHT = 480;
    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window - note animation is capped at 20FPS via the tickTimer
    @VisibleForTesting
    public static volatile boolean rendered = false;

    private final Semaphore renderLock = new Semaphore(1);
    private final Supplier<String> minecraftVersion;
    private final Supplier<String> neoForgeVersion;
    // Scheduled background rendering of the loading screen
    private final Future<?> automaticRendering;
    private final List<RenderElement> elements;
    private long lastFrameTime = 0;
    private boolean closed = false;

    public LoadingScreenRenderer(
            ScheduledExecutorService scheduler,
            Supplier<ELSRenderBackend> backend,
            Theme theme,
            @Nullable Path externalThemeDirectory,
            Supplier<String> minecraftVersion,
            Supplier<String> neoForgeVersion,
            boolean setupAutoRender) {
        super("FML Early Loading Screen", backend, theme, externalThemeDirectory, LAYOUT_WIDTH, LAYOUT_HEIGHT);
        this.minecraftVersion = minecraftVersion;
        this.neoForgeVersion = neoForgeVersion;
        this.elements = loadElements();
        this.backend.releaseContextOwnership();

        if (setupAutoRender) {
            this.automaticRendering = scheduler.scheduleWithFixedDelay(this::renderAutomatic, 50, 50, TimeUnit.MILLISECONDS);
        } else {
            this.automaticRendering = new CompletableFuture<>();
            this.automaticRendering.cancel(true);
        }
        // schedule a 50 ms ticker to try and smooth out the rendering
        scheduler.scheduleWithFixedDelay(() -> animationFrame++, 1, 50, TimeUnit.MILLISECONDS);
    }

    private void renderAutomatic() {
        if (!this.renderLock.tryAcquire()) {
            return;
        }
        try {
            this.backend.acquireContextOwnership(false);
            this.renderToScreen();
        } finally {
            this.backend.releaseContextOwnership(); // we release the gl context IF we're running off the main thread
            this.renderLock.release();
            rendered = true;
        }
    }

    public void renderToScreen() {
        try {
            long nanoTime = System.nanoTime();
            if (nanoTime - this.lastFrameTime > MINFRAMETIME) {
                this.lastFrameTime = nanoTime;
                this.renderToFramebuffer(this.theme.theme().colorScheme().screenBackground());
            }
        } catch (Throwable t) {
            LOGGER.error("Unexpected error while rendering the loading screen", t);
        }
    }

    @Override
    protected void renderToFramebuffer(RenderContext context) {
        for (RenderElement element : this.elements) {
            element.render(context);
        }
    }

    private List<RenderElement> loadElements() {
        List<RenderElement> elements = new ArrayList<>();

        ThemeLoadingScreen loadingScreen = this.theme.theme().loadingScreen();
        if (loadingScreen.background() != null && loadingScreen.background().visible()) {
            elements.add(new ImageElement(this.backend, loadingScreen.background(), this.theme));
        }
        if (loadingScreen.performance().visible()) {
            elements.add(new PerformanceElement(loadingScreen.performance(), this.theme));
        }
        if (loadingScreen.startupLog().visible()) {
            elements.add(new StartupLogElement(loadingScreen.startupLog(), this.theme));
        }
        if (loadingScreen.progressBars().visible()) {
            elements.add(new ProgressBarsElement(loadingScreen.progressBars(), this.theme));
        }
        if (loadingScreen.mojangLogo().visible()) {
            elements.add(new MojangLogoElement(this.backend, loadingScreen.mojangLogo(), this.theme));
        }

        // Add decorative elements
        for (Map.Entry<String, ThemeDecorativeElement> entry : loadingScreen.decoration().entrySet()) {
            ThemeDecorativeElement element = entry.getValue();
            if (!element.visible()) {
                continue; // Likely reconfigured in an extended theme
            }
            elements.add(loadElement(entry.getKey(), element));
        }

        return elements;
    }

    private RenderElement loadElement(String id, ThemeElement element) {
        RenderElement renderElement = switch (element) {
            case ThemeImageElement imageElement -> new ImageElement(this.backend, imageElement, this.theme);
            case ThemeLabelElement labelElement -> new LabelElement(labelElement, this.theme, () -> Map.of("version", getVersionString()));
            default -> throw new IllegalStateException("Unexpected theme element " + element + " of type " + element.getClass());
        };
        renderElement.setId(id);
        return renderElement;
    }

    private String getVersionString() {
        StringBuilder result = new StringBuilder();
        String minecraftVersion = this.minecraftVersion.get();
        if (minecraftVersion != null) {
            result.append(minecraftVersion);
        }
        String neoForgeVersion = this.neoForgeVersion.get();
        if (neoForgeVersion != null) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(neoForgeVersion.split("-")[0]);
        }
        return result.toString();
    }

    public void stopAutomaticRendering() throws TimeoutException, InterruptedException {
        if (this.automaticRendering.isCancelled()) {
            // Auto-render was already canceled, likely by window takeover, and we got here from the early error display triggering after window takeover
            return;
        }

        // We must acquire the render lock to ensure we cancel the future when the background thread is not currently
        // using the GL context. Otherwise, a race condition may occur when we cancel the future before the
        // glfwMakeContextCurrent(0) call at the end of renderToScreen. If the BG thread reads that the future is canceled,
        // it skips releasing the context. The main thread then crashes when it attempts to take ownership of the context.
        // Since renderToScreen bails immediately without running GL calls if it fails to acquire the lock,
        // we can safely assume the above won't happen once we have acquired it.
        if (!this.renderLock.tryAcquire(5, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        this.automaticRendering.cancel(false);
        this.renderLock.release();
    }

    @Override
    public void close(boolean destroyBackend) {
        if (!this.closed) {
            this.closed = true;

            this.backend.guardResourceCleanup(() -> {
                for (RenderElement element : this.elements) {
                    element.close();
                }
                super.close(destroyBackend);
            });
        }
    }

    public ELSRenderBackend getBackend() {
        return this.backend;
    }
}
