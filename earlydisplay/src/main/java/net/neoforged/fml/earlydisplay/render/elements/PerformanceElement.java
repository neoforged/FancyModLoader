package net.neoforged.fml.earlydisplay.render.elements;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.earlydisplay.render.GlState;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceElement extends RenderElement {
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceElement.class);

    private static final long REFRESH_AFTER_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private Future<Void> performanceUpdateFuture;
    private volatile PerformanceInfo currentPerformanceData;

    private final Texture barBackground;
    private final Texture barForeground;
    private final float[] lowColorHsb;
    private final float[] highColorHsb;

    public PerformanceElement(String id, MaterializedTheme theme, ThemePerformanceElement element) {
        super(id, theme);

        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        memoryBean = ManagementFactory.getMemoryMXBean();

        performanceUpdateFuture = CompletableFuture.runAsync(this::updatePerformanceData);

        this.barBackground = Texture.create(element.barBackground());
        this.barForeground = Texture.create(element.barForeground());

        this.lowColorHsb = element.lowColor().toHsb();
        this.highColorHsb = element.highColor().toHsb();
    }

    @Override
    public void render(RenderContext context) {
        var performanceData = currentPerformanceData;

        // Schedule an update if the performance data is outdated and there's no future running
        var now = System.nanoTime();
        if (performanceData != null && performanceData.createdNanos + REFRESH_AFTER_NANOS < now && performanceUpdateFuture.isDone()) {
            performanceUpdateFuture = CompletableFuture.runAsync(this::updatePerformanceData);
        } else if (performanceData == null) {
            return; // No data is available yet
        }

        var areaBounds = resolveBounds(context.availableWidth(), context.availableHeight(), 250, 50);
        float memoryBarFill = performanceData.memory();

        final int colour = ThemeColor.ofHsb(
                lowColorHsb[0] + (highColorHsb[0] - lowColorHsb[0]) * memoryBarFill,
                lowColorHsb[1] + (highColorHsb[1] - lowColorHsb[1]) * memoryBarFill,
                lowColorHsb[2] + (highColorHsb[2] - lowColorHsb[2]) * memoryBarFill).toArgb();

        var barBounds = new Bounds(
                areaBounds.left(),
                areaBounds.top(),
                areaBounds.right(),
                areaBounds.top() + barBackground.height());
        context.blitTexture(barBackground, barBounds);
        GlState.scissorTest(true);
        memoryBarFill = 0.5f;
        GlState.scissorBox(
                (int) barBounds.left(),
                (int) barBounds.top(),
                (int) (barBounds.width() * memoryBarFill),
                (int) barBounds.height());
        context.blitTexture(barForeground, barBounds, colour);
        GlState.scissorTest(false);

        // Draw the detailed performance text centered below the progress bar
        var textMeasurement = font.measureText(performanceData.text());
        context.renderText(
                (int) (areaBounds.horizontalCenter() - textMeasurement.width() / 2),
                barBounds.bottom(),
                font,
                List.of(
                        new SimpleFont.DisplayText(
                                performanceData.text(),
                                theme.theme().colorScheme().text().toArgb())));
    }

    @Override
    public void close() {
        super.close();
        performanceUpdateFuture.cancel(false);
    }

    private void updatePerformanceData() {
        try {
            var heapusage = memoryBean.getHeapMemoryUsage();
            var memory = (float) heapusage.getUsed() / heapusage.getMax();
            var cpuLoad = osBean.getProcessCpuLoad();
            String cpuText;
            if (cpuLoad == -1) {
                cpuText = String.format(Locale.ROOT, "*CPU: %d%%", Math.round(osBean.getCpuLoad() * 100f));
            } else {
                cpuText = String.format(Locale.ROOT, "CPU: %d%%", Math.round(cpuLoad * 100f));
            }

            var text = String.format(Locale.ROOT, "Memory: %d/%d MB (%d%%)  %s", heapusage.getUsed() >> 20, heapusage.getMax() >> 20, Math.round(memory * 100.0), cpuText);
            currentPerformanceData = new PerformanceInfo(System.nanoTime(), memory, text);
        } catch (Exception e) {
            LOG.error("Failed to update performance data.", e);
        }
    }

    private record PerformanceInfo(long createdNanos, float memory, String text) {}
}
