/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A launch target for bootstrapping a slim Minecraft environment in userdev, to be used in JUnit tests.
 *
 * @deprecated Use {@link JUnitDevLaunchTarget} instead.
 */
@Deprecated(forRemoval = true)
public class JUnitUserDevLaunchTarget extends NeoForgeUserdevLaunchHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JUnitUserDevLaunchTarget.class);

    @Override
    public Dist getDist() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        LOG.warn("Using deprecated launch target forgejunituserdev. Use forgejunitdev instead.");
        Class.forName(gameLayer.findModule("neoforge").orElseThrow(), "net.neoforged.neoforge.junit.JUnitMain").getMethod("main", String[].class).invoke(null, (Object) arguments);
    }

    @Override
    public String name() {
        return "forgejunituserdev";
    }
}
