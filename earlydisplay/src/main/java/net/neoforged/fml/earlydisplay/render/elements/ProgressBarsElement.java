/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.elements;

import java.util.List;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

public class ProgressBarsElement extends RenderElement {
    private static final int BAR_AREA_WIDTH = 400;
    private static final int BAR_AREA_HEIGHT = 200;

    private final ThemeProgressBarsElement settings;

    public ProgressBarsElement(ThemeProgressBarsElement settings, MaterializedTheme theme) {
        super(settings, theme);
        this.settings = settings;
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
                yOffset += font.lineSpacing() + settings.labelGap();
            }

            var barBounds = new Bounds(
                    areaBounds.left(),
                    areaBounds.top() + yOffset,
                    areaBounds.right(),
                    areaBounds.top() + yOffset + theme.sprites().progressBarBackground().height());

            if (progress.steps() == 0) {
                context.renderIndeterminateProgressBar(barBounds);
            } else {
                context.renderProgressBar(barBounds, progress.progress());
            }
            yOffset += barBounds.height() + settings.barGap();
        }
    }
}
