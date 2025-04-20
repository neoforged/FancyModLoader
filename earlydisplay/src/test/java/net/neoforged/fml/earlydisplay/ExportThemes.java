package net.neoforged.fml.earlydisplay;

import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeSerializer;

public class ExportThemes {
    public static void main(String[] args) throws Exception {
        var projectRoot = TestUtil.findProjectRoot();

        var defaultTheme = Theme.createDefaultTheme();
        var builtInThemePath = projectRoot.resolve("src/main/resources/net/neoforged/fml/earlydisplay/theme");
        ThemeSerializer.save(builtInThemePath.resolve("theme-default.json"), defaultTheme, false);

    }
}
