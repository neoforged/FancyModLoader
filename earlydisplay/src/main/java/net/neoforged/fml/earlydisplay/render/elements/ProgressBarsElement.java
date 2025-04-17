package net.neoforged.fml.earlydisplay.render.elements;

import java.util.List;
import net.neoforged.fml.earlydisplay.render.GlState;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

public class ProgressBarsElement extends RenderElement {
    private static final int BAR_AREA_WIDTH = 400;
    private static final int BAR_AREA_HEIGHT = 200;

    private final ThemeProgressBarsElement themeElement;
    private final Texture background;
    private final Texture foreground;
    private final Texture foregroundIndeterminate;

    public ProgressBarsElement(String id,
            MaterializedTheme theme,
            ThemeProgressBarsElement themeElement) {
        super(id, theme);
        this.background = Texture.create(themeElement.background());
        this.foreground = Texture.create(themeElement.foreground());
        this.foregroundIndeterminate = Texture.create(themeElement.foregroundIndeterminate());
        this.themeElement = themeElement;
    }

    @Override
    public void render(RenderContext context) {
        var areaBounds = resolveBounds(context.availableWidth(), context.availableHeight(), BAR_AREA_WIDTH, BAR_AREA_HEIGHT);

        float yOffset = 0;
        var barsShown = 0;
        for (var progress : StartupNotificationManager.getCurrentProgress()) {
            if (++barsShown > 2) {
                break;
            }

            String text = progress.label().getText();
            if (!text.isEmpty()) {
                context.renderText(
                        areaBounds.left(),
                        areaBounds.top() + yOffset,
                        font,
                        List.of(new SimpleFont.DisplayText(text, theme.theme().colorScheme().text().toArgb())));
                yOffset += font.lineSpacing() + themeElement.labelGap();
            }

            var barBounds = new Bounds(
                    areaBounds.left(),
                    areaBounds.top() + yOffset,
                    areaBounds.right(),
                    areaBounds.top() + yOffset + background.height());
            context.blitTexture(background, barBounds);

            if (progress.steps() == 0) {
                if (themeElement.indeterminateBounce()) {
                    // Indeterminate progress bars are rendered as a 20% piece that travels back and forth
                    var barX = 0;
                    var barWidth = (int) (barBounds.width() * 0.2f);
                    var availableSpace = (int) (barBounds.width() - barWidth);
                    if (availableSpace > 0) {
                        float f = (context.animationFrame() % 200) / 100.0f;
                        if (f > 1) {
                            f = 1 - (f - 1);
                        }
                        barX = (int) (f * availableSpace);
                    }
                    context.blitTexture(
                            foregroundIndeterminate,
                            barBounds.left() + barX,
                            barBounds.top(),
                            barWidth,
                            barBounds.height());
                } else {
                    // Indeterminate progress bars are rendered as a 20% piece that's scrolling left-to-right and then resets
                    var centerPercentage = (context.animationFrame() % 120) - 10;
                    var start = Math.clamp((centerPercentage - 10) / 100f, 0f, 1f);
                    var end = Math.clamp((centerPercentage + 10) / 100f, 0f, 1f);
                    context.blitTexture(
                            foregroundIndeterminate,
                            (int) (barBounds.left() + barBounds.width() * start),
                            barBounds.top(),
                            (int) (barBounds.width() * (end - start)),
                            barBounds.height());
                }
            } else {
                GlState.scissorTest(true);
                GlState.scissorBox(
                        (int) barBounds.left(),
                        (int) barBounds.top(),
                        (int) (barBounds.width() * progress.progress()),
                        (int) barBounds.height());
                context.blitTexture(foreground, barBounds);
                GlState.scissorTest(false);
            }
            yOffset += barBounds.height() + themeElement.barGap();
        }
    }
}
