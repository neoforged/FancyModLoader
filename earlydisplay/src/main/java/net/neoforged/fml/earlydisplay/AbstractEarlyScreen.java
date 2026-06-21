package net.neoforged.fml.earlydisplay;

import net.neoforged.fml.earlydisplay.render.EarlyFramebuffer;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.render.backend.ELSDrawCollector;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Supplier;

public abstract class AbstractEarlyScreen {
    private final String name;
    protected final ELSRenderBackend backend;
    protected final int screenWidth;
    protected final int screenHeight;
    protected final MaterializedTheme theme;
    protected final EarlyFramebuffer framebuffer;
    protected final SimpleBufferBuilder bufferBuilder;
    protected int animationFrame = 0;
    protected int offsetX = 0;
    protected int offsetY = 0;
    protected float scale = 1F;

    protected AbstractEarlyScreen(String name, Supplier<ELSRenderBackend> backend, Theme theme, @Nullable Path externalThemeDirectory, int screenWidth, int screenHeight) {
        this.name = name;
        this.backend = backend.get();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.theme = MaterializedTheme.materialize(this.backend, theme, externalThemeDirectory);
        this.backend.preloadPipelines(this.theme.shaders().values());
        this.framebuffer = new EarlyFramebuffer(this.backend, screenWidth, screenHeight);
        this.bufferBuilder = new SimpleBufferBuilder("shared_error", 8192);
    }

    protected final void renderToFramebuffer(ThemeColor clearColor) {
        if (!this.backend.startFrame(this.framebuffer::resize)) {
            return;
        }

        ELSDrawCollector collector = new ELSDrawCollector(this.backend);

        // Fit the layout rectangle into the screen while maintaining aspect ratio
        var desiredAspectRatio = this.screenWidth / (float) this.screenHeight;
        var actualAspectRatio = this.framebuffer.width() / (float) this.framebuffer.height();
        if (actualAspectRatio > desiredAspectRatio) {
            // This means we are wider than the desired aspect ratio, and have to center horizontally
            var actualWidth = desiredAspectRatio * this.framebuffer.height();
            this.offsetX = (int) (this.framebuffer.width() - actualWidth) / 2;
            this.offsetY = 0;
            collector.setViewport(this.offsetX, 0, (int) actualWidth, this.framebuffer.height());
            this.scale = (float) this.framebuffer.height() / this.screenHeight;
        } else {
            // This means we are taller than the desired aspect ratio, and have to center vertically
            var actualHeight = this.framebuffer.width() / desiredAspectRatio;
            this.offsetX = 0;
            this.offsetY = (int) (this.framebuffer.height() - actualHeight) / 2;
            collector.setViewport(0, offsetY, this.framebuffer.width(), (int) actualHeight);
            this.scale = (float) this.framebuffer.width() / this.screenWidth;
        }

        RenderContext context = new RenderContext(this.backend, collector, this.bufferBuilder, this.theme, this.screenWidth, this.screenHeight, this.offsetX, this.offsetY, this.scale, this.animationFrame);
        renderToFramebuffer(context);
        collector.execute(this.name, this.framebuffer.texture(), clearColor, this.bufferBuilder.getGpuBuffer(), this.screenWidth, this.screenHeight);

        this.backend.presentTexture(this.framebuffer.texture(), clearColor, this.framebuffer.width(), this.framebuffer.height());
    }

    protected abstract void renderToFramebuffer(RenderContext context);

    public void close(boolean destroyBackend) {
        this.theme.close();
        this.framebuffer.close();
        this.bufferBuilder.close();
        if (destroyBackend) {
            this.backend.close();
        }
    }
}
