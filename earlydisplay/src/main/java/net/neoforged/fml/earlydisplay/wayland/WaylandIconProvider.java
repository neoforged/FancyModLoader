/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.wayland;

import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaylandIconProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("WAYLANDICONPROVIDER");
    public static String APP_ID = "com.mojang.minecraft";
    public static String ICON_NAME = "neoforged_inject_icon.png";
    public static String DESKTOP_FILE_NAME = APP_ID + ".desktop";
    private static final List<Path> injects = new ArrayList<>();

    public static void injectIcon(@Nullable String mcVersion) {
        Runtime.getRuntime().addShutdownHook(new Thread(WaylandIconProvider::uninjectIcon));

        try (var iconRes = WaylandIconProvider.class.getResourceAsStream("/wayland/" + ICON_NAME);
                var desktopRes = WaylandIconProvider.class.getResourceAsStream("/wayland/" + DESKTOP_FILE_NAME)) {
            if (iconRes == null) throw new IOException("Failed to find icon resource");
            if (desktopRes == null) throw new IOException("Failed to find desktop resource");

            var iconByteStream = new ByteArrayOutputStream();
            iconRes.transferTo(iconByteStream);
            var image = ImageIO.read(new ByteArrayInputStream(iconByteStream.toByteArray()));
            var iconPath = getIconFileLocation(image.getWidth(), image.getHeight());
            if (iconPath == null) throw new IOException("Failed to resolve mc icon path");
            injectFile(iconPath, iconByteStream.toByteArray());

            var desktopString = String.format(new String(desktopRes.readAllBytes(), Charsets.UTF_8), mcVersion, ICON_NAME.substring(0, ICON_NAME.lastIndexOf(".")));
            var desktopPath = getDesktopFileLocation();
            if (desktopPath == null) throw new IOException("Failed to resolve desktop icon path");
            injectFile(desktopPath, desktopString.getBytes(Charsets.UTF_8));

            updateIconSystem();
        } catch (IOException | NullPointerException e) {
            LOGGER.error("Failed to inject icon", e);
        }
    }

    public static void uninjectIcon() {
        injects.forEach((path) -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOGGER.error("Failed to delete file", e);
            }
        });
    }

    private static void injectFile(Path target, byte[] data) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            injects.add(target);
        } catch (IOException e) {
            LOGGER.error("Failed to inject file", e);
        }
    }

    private static void updateIconSystem() {
        var procBuilder = new ProcessBuilder("xdg-icon-resource", "forceupdate");
        try {
            procBuilder.start();
        } catch (IOException e) {
            LOGGER.error("Failed to update icon system", e);
        }
    }

    @Nullable
    private static Path getIconFileLocation(int width, int height) {
        var userDataLocation = getUserDataLocation();
        if (userDataLocation == null) return null;

        return getUserDataLocation()
                .resolve("icons/hicolor")
                .resolve(width + "x" + height)
                .resolve("apps")
                .resolve(ICON_NAME);
    }

    @Nullable
    private static Path getDesktopFileLocation() {
        var userDataLocation = getUserDataLocation();
        if (userDataLocation == null) return null;

        return getUserDataLocation()
                .resolve("applications")
                .resolve(DESKTOP_FILE_NAME);
    }

    @Nullable
    private static Path getHome() {
        var home = System.getenv().getOrDefault("HOME", System.getProperty("user.home"));
        if (home == null || home.isEmpty()) {
            LOGGER.error("Failed to resolve user home");
            return null;
        }

        return Paths.get(home);
    }

    @Nullable
    private static Path getUserDataLocation() {
        var xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome == null || xdgDataHome.isEmpty()) {
            var home = getHome();
            return home != null ? getHome().resolve(".local/share/") : null;
        }
        return Paths.get(xdgDataHome);
    }
}
