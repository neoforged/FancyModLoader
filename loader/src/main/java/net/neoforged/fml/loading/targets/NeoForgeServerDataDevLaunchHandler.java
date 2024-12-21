/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

public class NeoForgeServerDataDevLaunchHandler extends NeoForgeDevLaunchHandler {
    @Override
    public String name() {
        return "neoforgeserverdatadev";
    }

    @Override
    public Dist getDist() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        runTarget("net.minecraft.data.Main", arguments, layer);
    }
}
