/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import cpw.mods.modlauncher.api.IEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
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

    <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass);

    List<String> modLists();

    List<String> mods();

    List<String> mavenRoots();

    /**
     * Checks if a given path was already found by a previous locator, or may be already loaded.
     */
    boolean isLocated(Path path);

    /**
     * Marks a path as being located and returns true if it was not previously located.
     */
    boolean addLocated(Path path);
}
