/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import java.util.Set;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.fml.loading.mixin.FMLMixinClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;

public class AccessTransformerService implements ClassProcessor {
    public final AccessTransformerEngine engine = AccessTransformerEngine.newEngine();

    public static final ProcessorName NAME = new ProcessorName("neoforge", "access_transformer");

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public Set<ProcessorName> runsBefore() {
        return Set.of(FMLMixinClassProcessor.NAME);
    }

    @Override
    public int processClassWithFlags(final TransformationContext context) {
        return engine.transform(context.node(), context.type()) ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
    }

    @Override
    public boolean handlesClass(final SelectionContext context) {
        return !context.empty() && engine.getTargets().contains(context.type());
    }
}
