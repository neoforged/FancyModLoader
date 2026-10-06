/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.earlywindow;

import net.neoforged.fml.loading.ProgramArgs;

/**
 * Defines a type which can be used to perform any bootstrap operations before
 * creating a window during the early loading window process.
 */
public interface GraphicsBootstrapper {
    /**
     * The name of this bootstrapper. This is used for logging purposes.
     *
     * @return The name of this bootstrapper.
     */
    String name();

    /// Performs any bootstrapping that needs to be done before creating a
    /// window.
    /// Non-standard program arguments only used by this bootstrapper should
    /// be removed from the provided args to avoid validation errors further
    /// downstream.
    ///
    /// @param arguments The arguments provided to the Java process. This is the
    ///                  entire command line, so you can process stuff from it.
    default void bootstrap(ProgramArgs arguments) {
        this.bootstrap(arguments.getArguments());
    }

    /// Performs any bootstrapping that needs to be done before creating a
    /// window.
    ///
    /// @param arguments The arguments provided to the Java process. This is the
    ///                  entire command line, so you can process stuff from it.
    void bootstrap(String[] arguments);
}
