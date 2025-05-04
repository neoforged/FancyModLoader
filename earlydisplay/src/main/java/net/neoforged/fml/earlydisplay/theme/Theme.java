/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;
import net.neoforged.fml.earlydisplay.util.StyleLength;

import java.util.Map;

/**
 * Defines a theme for the early display screen.
 *
 * @param windowIcon  The icon used for the loading screen operating system window until Minecraft takes over.
 * @param fonts       Defines font assets. Must contain a font named 'default', which is used if fonts are not overridden
 *                    for individual loading screen elements.
 * @param shaders     Defines the GLSL shaders used to draw elements of the loading screen. Overriding shaders is an advanced feature.
 * @param colorScheme Defines the color scheme used by the loading screen.
 * @param sprites     Defines various sprites used by the loading screen.
 */
public record Theme(
        ThemeResource windowIcon,
        Map<String, ThemeResource> fonts,
        Map<String, ThemeShader> shaders,
        ThemeColorScheme colorScheme,
        ThemeSprites sprites,
        ThemeLoadingScreen loadingScreen) {

    public static final String FONT_DEFAULT = "default";
    public static final String SHADER_GUI = "gui";
    public static final String SHADER_FONT = "font";
    public static final String SHADER_COLOR = "color";

    public static Theme createDefaultTheme() {
        var sprites = new ThemeSprites(
                new ThemeTexture(
                        classpathResource("progress_bar_bg.png"),
                        new TextureScaling.NineSlice(40, 20, 2, 2, 2, 2, true, true, false)),
                new ThemeTexture(
                        classpathResource("progress_bar_fg.png"),
                        new TextureScaling.NineSlice(40, 20, 4, 4, 4, 4, true, true, false)),
                new ThemeTexture(
                        classpathResource("progress_bar_fg.png"),
                        new TextureScaling.NineSlice(40, 20, 4, 4, 4, 4, true, true, false)),
                false);

        var squir = new ThemeImageElement();
        squir.setTexture(new ThemeTexture(classpathResource("squirrel.png"), new TextureScaling.Stretch(112, 112, true)));

        var startupLog = new ThemeStartupLogElement();
        startupLog.setLeft(StyleLength.ofPoints(10));
        startupLog.setBottom(StyleLength.ofPoints(10));

        var fox = new ThemeImageElement();
        fox.setTexture(
                new ThemeTexture(
                        classpathResource("fox_running.png"),
                        new TextureScaling.Stretch(151, 128, false),
                        new AnimationMetadata(28)));
        fox.setRight(StyleLength.ofPoints(10));
        fox.setBottom(StyleLength.ofREM(1));

        var forgeVersion = new ThemeLabelElement();
        forgeVersion.setText("${version}");
        forgeVersion.setBottom(StyleLength.ofPoints(10));
        forgeVersion.setRight(StyleLength.ofPoints(10));

        var progressBars = new ThemeProgressBarsElement();
        progressBars.setLabelGap(4);
        progressBars.setBarGap(5);
        progressBars.setLeft(StyleLength.ofPoints(220));
        progressBars.setRight(StyleLength.ofPoints(220));
        progressBars.setTop(StyleLength.ofPoints(250));
        progressBars.setMaintainAspectRatio(false);

        var performance = new ThemePerformanceElement();
        performance.setLeft(StyleLength.ofPoints(220));
        performance.setRight(StyleLength.ofPoints(220));
        performance.setTop(StyleLength.ofPoints(10));

        var mojangLogo = new ThemeMojangLogoElement();
        mojangLogo.setCenterHorizontally(true);
        mojangLogo.setTop(StyleLength.ofPoints(96));

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
                ThemeColorScheme.DEFAULT,
                sprites,
                new ThemeLoadingScreen(
                        performance,
                        progressBars,
                        startupLog,
                        mojangLogo,
                        Map.of(
                                "squir", squir,
                                "fox", fox,
                                "version", forgeVersion
                        )));
    }

    private static ClasspathResource classpathResource(String name) {
        return new ClasspathResource("net/neoforged/fml/earlydisplay/theme/" + name);
    }
}
