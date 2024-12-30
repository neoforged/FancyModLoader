/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import net.neoforged.fml.loading.ProgramArgs;
import org.slf4j.Logger;

import java.util.List;

public final class FMLStartupContext implements AutoCloseable {
    private final Logger logger;
    private final ProgramArgs programArgs;
    private final ClassLoader classLoader;
    private final List<AutoCloseable> resources;

    public FMLStartupContext(Logger logger,
                             ProgramArgs programArgs,
                             ClassLoader classLoader,
                             List<AutoCloseable> resources) {
        this.logger = logger;
        this.programArgs = programArgs;
        this.classLoader = classLoader;
        this.resources = resources;
    }

    public ClassLoader classLoader() {
        return classLoader;
    }

    public ProgramArgs programArgs() {
        return programArgs;
    }

    @Override
    public void close() {
        for (var resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                logger.error("Failed to close an FML resource on shutdown.", e);
            }
        }
    }
}
