/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi;

import net.neoforged.fml.loading.ProgramArgs;

/// Defines a type which can be used to perform any bootstrap operations before
/// mod discovery and loading starts.
public interface EarlyBootstrapper {
    /// {@return the name of this bootstrapper} This is used for logging purposes.
    String name();

    /// Performs any bootstrapping that needs to be done before mod discovery
    /// and loading starts.
    /// Non-standard program arguments only used by this bootstrapper should
    /// be removed from the provided args to avoid validation errors further
    /// downstream.
    ///
    /// @param arguments The arguments provided to the Java process. This is the
    ///                  entire command line, so you can process stuff from it.
    void bootstrap(ProgramArgs arguments);
}
