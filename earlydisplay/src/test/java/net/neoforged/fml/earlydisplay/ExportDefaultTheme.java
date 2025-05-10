/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeLoader;

public class ExportDefaultTheme {
    public static void main(String[] args) throws Exception {
        var projectRoot = TestUtil.findProjectRoot();

        var defaultTheme = Theme.createDefaultTheme();
        var builtInThemePath = projectRoot.resolve("src/main/resources/net/neoforged/fml/earlydisplay/theme");
        ThemeLoader.save(builtInThemePath.resolve("theme-default.json"), defaultTheme, false);
    }
}
