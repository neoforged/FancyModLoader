/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.jarhandling.VirtualJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.util.Constants;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class MixinSyntheticPackageProvider implements ITransformationService {
    @Override
    public @NotNull String name() {
        return "mixin-synthetic-package";
    }

    @Override
    public void initialize(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        try {
            return List.of(
                    new Resource(IModuleLayerManager.Layer.GAME, List.of(
                            new VirtualJar("mixinsynthetic", Path.of(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()),
                                    Constants.SYNTHETIC_PACKAGE, Constants.SYNTHETIC_PACKAGE + ".args")
                    ))
            );
        } catch (Exception exception) {
            throw new RuntimeException("Failed to intialise synthetic Mixin virtual jar", exception);
        }
    }

    @Override
    public @NotNull List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
