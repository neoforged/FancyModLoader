/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.junit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.startup.FmlInstrumentation;
import net.neoforged.fml.startup.JUnitGameBootstrapper;
import net.neoforged.fml.startup.StartupArgs;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * A session listener for JUnit environments that will bootstrap a Minecraft (FML) environment.
 */
public class JUnitService implements LauncherSessionListener {
    private Path gameDir;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // When the tests are started we want to make sure that they run on the transforming class loader which is set up by
        // bootstrapping BSL which will then load the launch target
        if (FMLLoader.currentOrNull() != null) {
            throw new IllegalStateException("Another FML loader is already current.");
        }

        var instrumentation = FmlInstrumentation.obtainInstrumentation();

        try {
            gameDir = Files.createTempDirectory("fml_junit");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create game directory", e);
        }

        FMLLoader.create(
                instrumentation,
                new StartupArgs(
                        gameDir,
                        gameDir.resolve(".cache"),
                        true,
                        null,
                        new String[] {},
                        Set.of(),
                        List.of(),
                        Thread.currentThread().getContextClassLoader()));

        for (var bootstrapper : ServiceLoader.load(JUnitGameBootstrapper.class)) {
            bootstrapper.bootstrap();
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        // Reset the loader in case JUnit wants to execute some pre-shutdown commands
        // and our custom class loader might throw it off
        var current = FMLLoader.currentOrNull();
        if (current != null) {
            current.close();
            // This is a slightly desparate attempt, but module loaders cannot be closed explicitly.
            // Instead, they have to be garbage collected to close the underlying Jar file.
            System.gc();
        }

        if (gameDir != null) {
            try {
                Files.walkFileTree(gameDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete temporary game directory", e);
            }
        }
    }
}
