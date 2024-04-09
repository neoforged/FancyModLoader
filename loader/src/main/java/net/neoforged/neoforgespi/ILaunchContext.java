/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import cpw.mods.modlauncher.api.IEnvironment;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides context for various FML plugins about the current launch operation.
 */
public interface ILaunchContext {
    Logger LOGGER = LoggerFactory.getLogger(ILaunchContext.class);

    /**
     * The Modlauncher environment.
     */
    IEnvironment environment();

    <T> ServiceLoader<T> createServiceLoader(Class<T> serviceClass);

    /**
     * Report a warning that does not prevent the launch from completing successfully but will be presented
     * to the player.
     */
    void reportWarning(); // TODO: Flesh out

    List<String> modLists();

    List<String> mods();

    List<String> mavenRoots();
}
