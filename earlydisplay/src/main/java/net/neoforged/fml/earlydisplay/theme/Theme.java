package net.neoforged.fml.earlydisplay.theme;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;
import net.neoforged.fml.earlydisplay.util.StyleLength;

/**
 * Defines a theme for the early display screen.
 */
public record Theme(
        ThemeResource windowIcon,
        Map<String, ThemeResource> fonts,
        Map<String, ThemeShader> shaders,
        List<ThemeElement> elements,
        ThemeColorScheme colorScheme) {

    public static final String FONT_DEFAULT = "default";
    public static final String SHADER_GUI = "gui";
    public static final String SHADER_FONT = "font";
    public static final String SHADER_COLOR = "color";
    public static Theme createDefaultTheme(boolean darkMode) {
        var squir = new ThemeImageElement();
        squir.setId("squir");
        squir.setTexture(new ThemeTexture(classpathResource("squirrel.png"), new TextureScaling.Stretch(112, 112)));

        var startupLog = new ThemeStartupLogElement();
        startupLog.setId("startupLog");
        startupLog.setLeft(StyleLength.ofPoints(10));
        startupLog.setBottom(StyleLength.ofPoints(10));

        var fox = new ThemeImageElement();
        fox.setId("fox");
        fox.setTexture(
                new ThemeTexture(
                        classpathResource("fox_running.png"),
                        new TextureScaling.Stretch(151, 128),
                        new AnimationMetadata(28)));
        fox.setRight(StyleLength.ofPoints(10));
        fox.setBottom(StyleLength.ofREM(1));

        var forgeVersion = new ThemeLabelElement();
        forgeVersion.setId("version");
        forgeVersion.setText("${version}");
        forgeVersion.setBottom(StyleLength.ofPoints(10));
        forgeVersion.setRight(StyleLength.ofPoints(10));

        var barBackground = new ThemeTexture(
                classpathResource("progress_bar_bg.png"),
                new TextureScaling.NineSlice(40, 20, 2, 2, 2, 2, true, true));
        var barForeground = new ThemeTexture(
                classpathResource("progress_bar_fg.png"),
                new TextureScaling.NineSlice(40, 20, 4, 4, 4, 4, true, true));
        var barIndeterminate = new ThemeTexture(
                classpathResource("progress_bar_fg.png"),
                new TextureScaling.NineSlice(40, 20, 4, 4, 4, 4, true, true));

        var progressBars = new ThemeProgressBarsElement();
        progressBars.setId("progressBars");
        progressBars.setBackground(barBackground);
        progressBars.setForeground(barForeground);
        progressBars.setForegroundIndeterminate(barIndeterminate);
        progressBars.setLabelGap(4);
        progressBars.setBarGap(5);
        progressBars.setLeft(StyleLength.ofPoints(220));
        progressBars.setRight(StyleLength.ofPoints(220));
        progressBars.setTop(StyleLength.ofPoints(250));
        progressBars.setMaintainAspectRatio(false);

        var performance = new ThemePerformanceElement();
        performance.setId("performance");
        performance.setBarBackground(barBackground);
        performance.setBarForeground(barForeground);
        performance.setLowColor(ThemeColor.ofBytes(0, 127, 0));
        performance.setHighColor(ThemeColor.ofBytes(255, 127, 0));
        performance.setLeft(StyleLength.ofPoints(220));
        performance.setRight(StyleLength.ofPoints(220));
        performance.setTop(StyleLength.ofPoints(10));

        return new Theme(
                classpathResource("neoforged_icon.png"),
                Map.of(
                        FONT_DEFAULT, classpathResource("Monocraft.ttf")),
                Map.of(
                        SHADER_GUI,
                        ThemeShader.DEFAULT_GUI,
                        SHADER_FONT,
                        ThemeShader.DEFAULT_FONT,
                        SHADER_COLOR,
                        ThemeShader.DEFAULT_COLOR),
                List.of(squir, fox, startupLog, forgeVersion, progressBars, performance),
                ThemeColorScheme.DEFAULT);
    }

    private static ClasspathResource classpathResource(String name) {
        return new ClasspathResource("net/neoforged/fml/earlydisplay/theme/" + name);
    }
}
