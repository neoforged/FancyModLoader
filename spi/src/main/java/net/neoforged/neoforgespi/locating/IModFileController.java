/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * A mod file controller controller handles validating and computing information about the mods contained by the file. <p>
 * Controllers are mostly internal, for use by FML, and should only be used by implementors.
 */
@ApiStatus.OverrideOnly
public interface IModFileController {
    /**
     * Called when the mods contained in the mod file shall be identified, and any other related information (such as AT files).
     */
    void identify();

    /**
     * Sets the loaders that will load the mod. <p>
     * The loaders that will be received are defined by {@link IModFileInfo#requiredLanguageLoaders()}.
     *
     * @param loaders the loaders that will load the mod
     */
    void setLoaders(List<IModLanguageProvider> loaders);

    /**
     * Submit this file for content scanning. This includes computing the {@link IModFile#getScanResult() scan data} of the file.
     */
    CompletableFuture<?> submitForScanning(ExecutorService service);
}
