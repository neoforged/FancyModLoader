/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Use {@link NeoForgeServerDevLaunchHandler} instead.
 */
@Deprecated(forRemoval = true)
public class NeoForgeServerUserdevLaunchHandler extends NeoForgeUserdevLaunchHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeServerUserdevLaunchHandler.class);

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
        LOG.warn("Using deprecated launch target forgeserveruserdev. Use forgeserverdev instead.");
        serverService(arguments, layer);
    }
}
