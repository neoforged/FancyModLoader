/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.error.ErrorDisplay;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.opengl.GL;

public class TestErrorDisplay {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        //System.setProperty("fml.earlyWindowDarkMode", "true");
        System.setProperty("fml.loadingErrorThrowOnExit", "true");

        FMLPaths.loadAbsolutePaths(TestEarlyDisplay.findProjectRoot());
        FMLConfig.load();

        var window = new DisplayWindow();
        var periodicTick = window.initialize(new String[] {
                "--fml.mcVersion", "1.21.5",
                "--fml.neoForgeVersion", "21.5.123-beta"
        });

        // Render once, then take over the window to display the error window
        periodicTick.run();
        long windowId = window.takeOverGlfwWindow();
        GL.createCapabilities();
        window.close();

        List<ModLoadingIssue> issues = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            issues.add(new ModLoadingIssue(
                    ModLoadingIssue.Severity.ERROR,
                    "fml.modloadingissue.failedtoloadmod",
                    List.of(),
                    new UnsupportedOperationException(),
                    null,
                    null,
                    null));
        }
        ErrorDisplay.fatal(windowId, issues, Path.of("./tests/mods"), Path.of("./logs/latest.log"), Path.of("./"));
    }
}
