package net.neoforged.fml.earlydisplay;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestEarlyDisplay {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("fml.writeMissingTheme", "true");

        // Find the project directory by search for build.gradle upwards
        var projectRoot = findProjectRoot(Paths.get(TestEarlyDisplay.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        FMLPaths.loadAbsolutePaths(projectRoot);

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
                throw new RuntimeException(e);
            }
        }
    }

    private static Path findProjectRoot(Path path) {
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
