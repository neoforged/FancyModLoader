package net.neoforged.fml.junit;

import cpw.mods.bootstraplauncher.BootstrapLauncher;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.nio.file.Files;
import java.nio.file.Path;

public class JUnitService implements LauncherSessionListener {
    private ClassLoader oldLoader;
    public JUnitService() {
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        oldLoader = Thread.currentThread().getContextClassLoader();

        try {
            final String[] args = Files.readAllLines(Path.of("mainargs.txt")).toArray(String[]::new);
            BootstrapLauncher.main(args);
        } catch (Exception exception) {
            System.err.println("Failed to start Minecraft: " + exception);
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        Thread.currentThread().setContextClassLoader(oldLoader);
    }
}
