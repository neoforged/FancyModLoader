/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

/**
 * A launch target for bootstrapping a slim Minecraft environment in forgedev, to be used in JUnit tests.
 */
public class JUnitDevLaunchTarget extends NeoForgeDevLaunchHandler {
    @Override
    public Dist getDist() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        Class.forName(gameLayer.findModule("neoforge").orElseThrow(), "net.neoforged.neoforge.junit.JUnitMain").getMethod("main", String[].class).invoke(null, (Object) arguments);
    }

    @Override
    public String name() {
        return "neoforgejunitdev";
    }
}
