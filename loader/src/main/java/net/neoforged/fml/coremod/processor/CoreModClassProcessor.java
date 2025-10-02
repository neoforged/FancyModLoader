/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod.processor;

import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.fml.coremod.CoreModClassTransformer;

public final class CoreModClassProcessor extends CoreModBaseProcessor {
    private final CoreModClassTransformer transformer;
    private final Set<String> targets;

    public CoreModClassProcessor(CoreModClassTransformer transformer) {
        super(transformer);
        this.transformer = transformer;
        this.targets = transformer.targets().stream().map(CoreModClassTransformer.Target::className).collect(Collectors.toSet());
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return targets.contains(context.type().getClassName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        transformer.transform(context.node(), context);
        return ComputeFlags.COMPUTE_FRAMES;
    }

    @Override
    public String toString() {
        return "class processor for " + transformer;
    }
}
