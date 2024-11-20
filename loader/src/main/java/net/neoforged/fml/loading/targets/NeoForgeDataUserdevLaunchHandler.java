/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

/**
 * @deprecated Use {@link NeoForgeDataDevLaunchHandler} instead.
 */
@Deprecated(forRemoval = true)
public class NeoForgeDataUserdevLaunchHandler extends CommonDevLaunchHandler {
    @Override
    public String name() {
        return "forgedatauserdev";
    }

    @Override
    public Dist getDist() {
        return Dist.CLIENT;
    }

    @Override
    public boolean isData() {
        return true;
    }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        dataService(arguments, layer);
    }
}
