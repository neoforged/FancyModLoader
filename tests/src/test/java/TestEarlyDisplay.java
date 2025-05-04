/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.FMLPaths;

public class TestEarlyDisplay {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("fml.earlyWindowDarkMode", "true");

        FMLPaths.loadAbsolutePaths(findProjectRoot());

        var window = new DisplayWindow();
        var periodicTick = window.initialize(new String[] {
                "--fml.mcVersion", "1.21.5",
                "--fml.neoForgeVersion", "21.5.123-beta"
        });

        while (true) {
            try {
                periodicTick.run();
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    static Path findProjectRoot() throws Exception {
        // Find the project directory by search for build.gradle upwards
        return findProjectRoot(Paths.get(TestEarlyDisplay.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }

    static Path findProjectRoot(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalArgumentException("Couldn't find buid.gradle in any parent directory of " + path);
    }
}
