/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.installation;

import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.Nullable;

/**
 * A service which can be used to discover or install the game at runtime.
 * <p>
 * When the game launches it tries to discover the NeoForge universal jar and the patched minecraft
 * jar from the folder which contains the libraries for the game.
 * </p>
 * <p>
 * The first step will be to validate the libraries folder, and then find the relevant neoforge jar
 * in there. Secondly it will then parse its neoforge.mods.toml and prepare the launch.
 * </p>
 * <p>
 * When that all succeeds the game tries to find the relevant patched minecraft jar in the folder as well,
 * as it expects this jar to be put there by the installer.
 * If the jar exists it will continue and load the neoforge.mods.toml from that jar and continue the normal
 * loading procedure.
 * </p>
 * <p>
 * If it can not find the patched minecraft jar (because the installer did not create the file), then it will
 * check if a service of this type is found as an early loader service (if multiple are found a launch argument
 * 'fml.installer' is used to differentiate and select the requested instance). Then it will call
 * {@link GameDiscoveryOrInstallationService#discoverOrInstall(String, Dist)} to handle the discovery or installation.
 * </p>
 * <p>
 * Each implementation of this type is responsible on its own for caching its results.
 * </p>
 * <p>
 * If the launch argument 'fml.disableInstaller' is provided then this entire subsystem is disabled and the loader
 * will not try to invoke or even discover and instantiate implementations of this type.
 * </p>
 */
public interface GameDiscoveryOrInstallationService {
    /**
     * {@return The name of the service}
     */
    String name();

    /**
     * Invoked to discover or install the game when it is not found in the libraries folder.
     *
     * @param requiredDist    The distribution which should be discovered or installed.
     * @return The {@link Result} of discovery or installation. {@code null} if not found or installed.
     */
    @Nullable
    Result discoverOrInstall(Dist requiredDist) throws Exception;

    /**
     * The result of the discovery or installation.
     *
     * @param minecraft The path to the patched minecraft jar.
     */
    record Result(Path minecraft) {}
}
