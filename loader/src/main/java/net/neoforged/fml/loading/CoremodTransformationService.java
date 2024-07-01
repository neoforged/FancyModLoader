/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.List;
import java.util.Set;

@Deprecated(forRemoval = true)
final class CoremodTransformationService implements ITransformationService {
    static final CoremodTransformationService INSTANCE = new CoremodTransformationService();

    @Override
    public String name() {
        return "FML coremods";
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
