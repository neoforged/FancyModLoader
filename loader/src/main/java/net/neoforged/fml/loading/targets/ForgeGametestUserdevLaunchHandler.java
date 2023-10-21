/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

public class ForgeGametestUserdevLaunchHandler extends ForgeUserdevLaunchHandler {
    @Override public String name() { return "forgegametestserveruserdev"; }
    @Override public Dist getDist() { return Dist.DEDICATED_SERVER; }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        Class.forName(layer.findModule("neoforge").orElseThrow(), "net.neoforged.gametest.GameTestMain").getMethod("main", String[].class).invoke(null, (Object)arguments);
    }
}