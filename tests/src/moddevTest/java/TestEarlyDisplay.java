/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

public class TestEarlyDisplay {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        //System.setProperty("fml.earlyWindowDarkMode", "true");

        FMLPaths.loadAbsolutePaths(findProjectRoot());
        FMLConfig.load();

        var window = new DisplayWindow();
        window.initialize(ProgramArgs.from());
        Runnable periodicTick = window::periodicTick;

        window.setMinecraftVersion("1.21.5");
        window.setNeoForgeVersion("21.5.123-beta");

        AtomicBoolean closed = new AtomicBoolean(false);

        // Render once, then take over the window to test that it still works
        periodicTick.run();
        long windowId = window.takeOverGlfwWindow();

        // The context moves to the main thread now
        GL.createCapabilities();

        GLFW.glfwSetWindowCloseCallback(windowId, window1 -> {
            window.close();
            closed.set(true);
        });

        StartupNotificationManager.addProgressBar("Test Bar", 20).setAbsolute(10);

        while (!closed.get()) {
            try {
                periodicTick.run();
                Thread.sleep(20L);
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
