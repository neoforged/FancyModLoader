/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.junit;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * A session listener for JUnit environments that will bootstrap a Minecraft (FML) environment.
 */
public class JUnitService implements LauncherSessionListener {
    private ClassLoader oldLoader;

    public JUnitService() {}

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // When the tests are started we want to make sure that they run on the transforming class loader which is set up by
        // bootstrapping BSL which will then load the launch target
        oldLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(LaunchWrapper.getTransformingLoader());
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        // Reset the loader in case JUnit wants to execute some pre-shutdown commands
        // and our custom class loader might throw it off
        Thread.currentThread().setContextClassLoader(oldLoader);
    }
}
