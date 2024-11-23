/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Use {@link NeoForgeClientDevLaunchHandler} instead.
 */
@Deprecated(forRemoval = true)
public class NeoForgeClientUserdevLaunchHandler extends NeoForgeUserdevLaunchHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeClientUserdevLaunchHandler.class);

    @Override
    public String name() {
        return "forgeclientuserdev";
    }

    @Override
    public Dist getDist() {
        return Dist.CLIENT;
    }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        LOG.warn("Using deprecated launch target forgeclientuserdev. Use forgeclientdev instead.");
        clientService(arguments, layer);
    }
}
