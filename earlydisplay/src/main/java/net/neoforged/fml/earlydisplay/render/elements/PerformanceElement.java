package net.neoforged.fml.earlydisplay.render.elements;

import com.sun.management.OperatingSystemMXBean;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PerformanceElement extends RenderElement {
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceElement.class);

    private static final long REFRESH_AFTER_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private Future<Void> performanceUpdateFuture;
    private volatile PerformanceInfo currentPerformanceData;

    public PerformanceElement(ThemePerformanceElement settings, MaterializedTheme theme) {
        super(settings, theme);

        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        memoryBean = ManagementFactory.getMemoryMXBean();

        performanceUpdateFuture = CompletableFuture.runAsync(this::updatePerformanceData);
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

        // Interpolate between the low/high colors set in the theme based on current memory usage
        var color = ThemeColor.lerp(
                theme.theme().colorScheme().memoryLowColor(),
                theme.theme().colorScheme().memoryHighColor(),
                memoryBarFill
        );

        var barBounds = new Bounds(
                areaBounds.left(),
                areaBounds.top(),
                areaBounds.right(),
                areaBounds.top() + theme.sprites().progressBarBackground().height());

        context.renderProgressBar(barBounds, memoryBarFill, color.toArgb());

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

    private record PerformanceInfo(long createdNanos, float memory, String text) {
    }
}
