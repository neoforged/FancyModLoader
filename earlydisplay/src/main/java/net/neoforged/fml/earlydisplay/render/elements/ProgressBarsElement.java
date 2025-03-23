package net.neoforged.fml.earlydisplay.render.elements;

import java.util.List;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

public class ProgressBarsElement extends RenderElement {
    private final Theme theme;

    public ProgressBarsElement(String id, Theme theme) {
        super(id);
        this.theme = theme;
    }

    private static final int BAR_HEIGHT = 20;
    private static final int BAR_WIDTH = 400;

    @Override
    public void render(RenderContext context) {
        var font = context.getFont(Theme.FONT_DEFAULT);

        var alpha = 0xFF;
        var barsShown = 0;
        for (var progress : StartupNotificationManager.getCurrentProgress()) {
            if (++barsShown > 2) {
                break;
            }

            var bounds = resolveBounds(context.availableWidth(), context.availableHeight(), BAR_WIDTH, BAR_HEIGHT);

            context.renderProgressBar(
                    bounds,
                    barsShown,
                    1f,
                    font,
                    progress,
                    theme.colorScheme().background(),
                    theme.colorScheme().text());
            context.renderText(
                    bounds.left(),
                    bounds.bottom(),
                    font,
                    List.of(
                            new SimpleFont.DisplayText(progress.label().getText(), theme.colorScheme().text().toArgb())));
        }
    }
}
