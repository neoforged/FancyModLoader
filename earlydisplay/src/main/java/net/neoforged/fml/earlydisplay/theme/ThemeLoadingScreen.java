/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeDecorativeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;

/**
 * Describes the themable properties of the loading screen.
 */
public record ThemeLoadingScreen(
        ThemePerformanceElement performance,
        ThemeProgressBarsElement progressBars,
        ThemeStartupLogElement startupLog,
        ThemeMojangLogoElement mojangLogo,
        Map<String, ThemeDecorativeElement> decoration) {}
