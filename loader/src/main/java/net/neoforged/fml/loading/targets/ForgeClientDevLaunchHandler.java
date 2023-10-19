/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

public class ForgeClientDevLaunchHandler extends CommonDevLaunchHandler {
    @Override public String name() { return "forgeclientdev"; }
    @Override public Dist getDist() { return Dist.CLIENT; }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        clientService(arguments, layer);
    }
}
