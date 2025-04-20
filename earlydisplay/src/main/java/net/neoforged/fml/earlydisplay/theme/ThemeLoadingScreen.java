package net.neoforged.fml.earlydisplay.theme;

import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;

/**
 * Describes the themable properties of the loading screen.
 */
public record ThemeLoadingScreen(
        ThemePerformanceElement performance,
        ThemeProgressBarsElement progressBars,
        ThemeStartupLogElement startupLog) {}
