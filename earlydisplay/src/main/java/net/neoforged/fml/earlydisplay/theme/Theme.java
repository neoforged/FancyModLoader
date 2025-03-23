package net.neoforged.fml.earlydisplay.theme;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;
import net.neoforged.fml.earlydisplay.util.StyleLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a theme for the early display screen.
 */
public record Theme(
        UncompressedImage windowIcon,
        Map<String, ThemeResource> fonts,
        Map<String, ThemeShader> shaders,
        List<ThemeElement> elements,
        ThemeColorScheme colorScheme) implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Theme.class);
    public static final String FONT_DEFAULT = "default";
    public static final String SHADER_GUI = "gui";
    public static final String SHADER_FONT = "font";
    public static final String SHADER_COLOR = "color";
    public static Theme createDefaultTheme(boolean darkMode) {
        var squir = new ThemeImageElement(
                "squir",
                new ThemeTexture(new ClasspathResource("/squirrel.png")));

        var startupLog = new ThemeStartupLogElement("startupLog");
        startupLog.setLeft(StyleLength.ofPoints(10));
        startupLog.setBottom(StyleLength.ofPoints(10));

        var fox = new ThemeImageElement(
                "fox",
                new ThemeTexture(new ClasspathResource("/fox_running.png"), new AnimationMetadata(28)));
        fox.setRight(StyleLength.ofPoints(10));
        fox.setBottom(StyleLength.ofPoints(10));

        var forgeVersion = new ThemeLabelElement("version", "${version}");
        forgeVersion.setBottom(StyleLength.ofPoints(10));
        forgeVersion.setRight(StyleLength.ofPoints(10));

        var progressBars = new ThemeProgressBarsElement("progressBars");
        progressBars.setRight(StyleLength.ofPoints(400));
        progressBars.setTop(StyleLength.ofPoints(250));

        return new Theme(
                ImageLoader.loadImage(new ClasspathResource("/neoforged_icon.png")),
                Map.of(
                        FONT_DEFAULT, new ClasspathResource("/Monocraft.ttf")),
                Map.of(
                        SHADER_GUI,
                        ThemeShader.DEFAULT_GUI,
                        SHADER_FONT,
                        ThemeShader.DEFAULT_FONT,
                        SHADER_COLOR,
                        ThemeShader.DEFAULT_COLOR),
                List.of(squir, fox, startupLog, forgeVersion, progressBars),
                ThemeColorScheme.DEFAULT);
    }

    public static Theme load(File path, boolean darkMode) {
        var properties = new Properties();
        try (var in = new BufferedInputStream(new FileInputStream(path))) {
            properties.load(in);
            // TODO: actually load custom theme
        } catch (FileNotFoundException e) {
            LOGGER.error("Failed to find theme {}", path);
        } catch (IOException e) {
            LOGGER.error("Failed to read loading window theme from {}", path, e);
        }

        return createDefaultTheme(darkMode);
    }

    @Override
    public void close() {
        windowIcon.close();
    }
}
