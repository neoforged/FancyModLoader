/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;


import net.neoforged.fml.earlydisplay.theme.elements.*;

import java.util.Map;

/**
 * Describes the themable properties of the loading screen.
 */
public record ThemeLoadingScreen(ThemeImageElement background, ThemePerformanceElement performance,
                                 ThemeProgressBarsElement progressBars, ThemeStartupLogElement startupLog,
                                 ThemeMojangLogoElement mojangLogo, Map<String, ThemeDecorativeElement> decoration) {
}
