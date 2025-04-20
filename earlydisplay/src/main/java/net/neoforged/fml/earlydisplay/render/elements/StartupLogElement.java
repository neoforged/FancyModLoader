package net.neoforged.fml.earlydisplay.render.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;
import net.neoforged.fml.earlydisplay.util.Bounds;
import net.neoforged.fml.earlydisplay.util.Size;
import net.neoforged.fml.loading.progress.Message;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

public class StartupLogElement extends RenderElement {
    private ThemeColor textColor;

    public StartupLogElement(ThemeStartupLogElement settings, MaterializedTheme theme) {
        super(settings, theme);
        this.textColor = Objects.requireNonNullElseGet(textColor, () -> theme.theme().colorScheme().text());
    }

    @Override
    public void render(RenderContext context) {
        List<StartupNotificationManager.AgeMessage> messages = StartupNotificationManager.getMessages();
        List<SimpleFont.DisplayText> texts = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            final StartupNotificationManager.AgeMessage pair = messages.get(i);
            final float fade = clamp((4000.0f - (float) pair.age() - (i - 4) * 1000.0f) / 5000.0f, 0.0f, 1.0f);
            if (fade < 0.01f) {
                continue;
            }
            Message msg = pair.message();
            int colour = textColor.withAlpha(fade).toArgb();
            texts.add(new SimpleFont.DisplayText(msg.getText() + "\n", colour));
        }

        var intrinsicSize = getIntrinsicSize(texts, font);
        var bounds = resolveBounds(context.availableWidth(), context.availableHeight(), intrinsicSize.width(), intrinsicSize.height());

        context.renderText(bounds.left(), bounds.top(), font, texts);
    }

    private static Size getIntrinsicSize(List<SimpleFont.DisplayText> texts, SimpleFont font) {
        var bounds = new Bounds(0, 0, 0, 0);
        for (var text : texts) {
            bounds = bounds.union(
                    new Bounds(0, bounds.bottom(), font.measureText(text.string())));
        }
        return new Size(bounds.width(), bounds.height());
    }
}
