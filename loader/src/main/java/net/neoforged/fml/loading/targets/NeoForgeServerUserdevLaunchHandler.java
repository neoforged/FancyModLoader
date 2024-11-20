/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

/**
 * @deprecated Use {@link NeoForgeServerDevLaunchHandler} instead.
 */
@Deprecated(forRemoval = true)
public class NeoForgeServerUserdevLaunchHandler extends CommonDevLaunchHandler {
    @Override
    public String name() {
        return "forgeserveruserdev";
    }

    @Override
    public Dist getDist() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        serverService(arguments, layer);
    }
}
