/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

import net.neoforged.fml.junit.JUnitService;
import org.junit.platform.launcher.LauncherSessionListener;

module net.neoforged.fml.junit {
    requires org.junit.platform.launcher;
    requires cpw.mods.bootstraplauncher;

    provides LauncherSessionListener with JUnitService;
}
