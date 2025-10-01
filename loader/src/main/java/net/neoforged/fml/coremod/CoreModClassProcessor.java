/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforgespi.transformation.ClassProcessorBehavior;

final class CoreModClassProcessor implements ClassProcessorBehavior {
    private final CoreModClassTransformer transformer;
    private final Set<String> targets;

    CoreModClassProcessor(CoreModClassTransformer transformer) {
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
