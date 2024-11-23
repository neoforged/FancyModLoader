/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Use {@link NeoForgeDataDevLaunchHandler} instead.
 */
@Deprecated(forRemoval = true)
public class NeoForgeDataUserdevLaunchHandler extends NeoForgeUserdevLaunchHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeDataUserdevLaunchHandler.class);

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
        LOG.warn("Using deprecated launch target forgedatauserdev. Use forgedatadev instead.");
        dataService(arguments, layer);
    }
}
