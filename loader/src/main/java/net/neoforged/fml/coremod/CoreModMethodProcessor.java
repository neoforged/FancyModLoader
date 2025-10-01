/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforgespi.transformation.ClassProcessorBehavior;

final class CoreModMethodProcessor implements ClassProcessorBehavior {
    private final CoreModMethodTransformer transformer;
    private final Map<String, Set<String>> targetsByClass;

    CoreModMethodProcessor(CoreModMethodTransformer transformer) {
        this.transformer = transformer;
        this.targetsByClass = transformer.targets().stream().collect(
                Collectors.groupingBy(CoreModMethodTransformer.Target::className, Collectors.mapping(
                        t -> t.methodName() + t.methodDescriptor(),
                        Collectors.toSet())));
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return targetsByClass.containsKey(context.type().getClassName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        var targetMethods = this.targetsByClass.get(context.type().getClassName());
        if (targetMethods == null) {
            return ComputeFlags.NO_REWRITE;
        }

        boolean transformed = false;
        for (var method : context.node().methods) {
            if (targetMethods.contains(method.name + method.desc)) {
                transformer.transform(method, context);
                transformed = true;
            }
        }

        return transformed ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
    }

    @Override
    public String toString() {
        return "class processor for " + transformer;
    }
}
