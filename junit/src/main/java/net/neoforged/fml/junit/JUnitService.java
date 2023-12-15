/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.junit;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class JUnitService implements LauncherSessionListener {
    private ClassLoader oldLoader;
    public JUnitService() {

    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        oldLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(LaunchWrapper.getTransformingLoader());
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        Thread.currentThread().setContextClassLoader(oldLoader);
    }
}
