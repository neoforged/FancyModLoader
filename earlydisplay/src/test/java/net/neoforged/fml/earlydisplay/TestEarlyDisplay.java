package net.neoforged.fml.earlydisplay;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Paths;

public class TestEarlyDisplay {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("fml.earlyWindowDarkMode", "true");

        FMLPaths.loadAbsolutePaths(TestUtil.findProjectRoot());

        var window = new DisplayWindow();
        var periodicTick = window.initialize(new String[]{
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
}
