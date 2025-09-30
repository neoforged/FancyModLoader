/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import java.util.Set;
import net.neoforged.fml.coremod.CoreModTransformer;
import net.neoforged.fml.loading.mixin.FMLMixinClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class CoreModsGroup implements ClassProcessor {
    // For ordering purposes only; allows making transformers that run before/after all "default" coremods
    @Override
    public ProcessorName name() {
        return CoreModTransformer.COREMODS_GROUP;
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return false;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return Set.of(COMPUTING_FRAMES, FMLMixinClassProcessor.NAME);
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        return ComputeFlags.NO_REWRITE;
    }
}
