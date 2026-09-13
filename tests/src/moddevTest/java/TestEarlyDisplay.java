/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.render.LoadingScreenRenderer;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.opengl.GlRenderer;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.sdl.SDLInit;

public class TestEarlyDisplay {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("fml.earlyWindowDarkMode", "true");

        FMLPaths.loadAbsolutePaths(findProjectRoot());
        FMLConfig.load();

        var window = new DisplayWindow();
        window.initialize(ProgramArgs.from());
        Runnable periodicTick = window::periodicTick;

        window.setMinecraftVersion("26.3");
        window.setNeoForgeVersion("26.3.0.0-alpha");

        // Render once, then take over the window to test that it still works
        while (!LoadingScreenRenderer.rendered) {
            try {
                periodicTick.run();
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long windowHandle = window.getWindowHandle();
        ELSRenderBackend[] backend = new ELSRenderBackend[1];
        window.handOverToMinecraft(() -> backend[0] = GlRenderer.setupBackend(windowHandle), false);
        backend[0].acquireContextOwnership(true);

        StartupNotificationManager.addProgressBar("Test Bar", 20).setAbsolute(10);
        StartupNotificationManager.addProgressBar("More Test Bar", 0);

        while (!window.isClosed()) {
            try {
                periodicTick.run();
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_VIDEO);
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
