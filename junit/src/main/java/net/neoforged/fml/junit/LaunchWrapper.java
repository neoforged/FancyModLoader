/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.junit;

import cpw.mods.bootstraplauncher.BootstrapLauncher;
import java.nio.file.Files;
import java.nio.file.Path;

public class LaunchWrapper {
    private static ClassLoader transformingCL;

    /**
     * Lazily get the transforming module class loader used by the game at runtime by loading the game when needed.
     * <p>
     * Since in a JUnit environment we can't easily get the launch arguments, we will load them from the file specified via the {@code fml.junit.argsfile} system property.
     */
    public static synchronized ClassLoader getTransformingLoader() {
        if (transformingCL != null) return transformingCL;
        final var oldLoader = Thread.currentThread().getContextClassLoader();

        try {
            final String[] args = Files.readAllLines(Path.of(System.getProperty("fml.junit.argsfile", "mainargs.txt"))).toArray(String[]::new);
            BootstrapLauncher.unitTestingMain(args);

            transformingCL = Thread.currentThread().getContextClassLoader();
        } catch (Exception exception) {
            System.err.println("Failed to start Minecraft: " + exception);
            throw new RuntimeException(exception);
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }

        return transformingCL;
    }
}
