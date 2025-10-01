/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforgespi.transformation.ClassProcessor;

final class CoreModFieldProcessor extends CoreModBaseProcessor {
    private final CoreModFieldTransformer transformer;
    private final Map<String, Set<String>> targetsByClass;

    CoreModFieldProcessor(CoreModFieldTransformer transformer) {
        super(transformer);
        this.transformer = transformer;
        this.targetsByClass = transformer.targets().stream().collect(
                Collectors.groupingBy(CoreModFieldTransformer.Target::className, Collectors.mapping(
                        CoreModFieldTransformer.Target::fieldName,
                        Collectors.toSet())));
    }

    @Override
    public boolean handlesClass(ClassProcessor.SelectionContext context) {
        return targetsByClass.containsKey(context.type().getClassName());
    }

    @Override
    public ClassProcessor.ComputeFlags processClass(TransformationContext context) {
        var targetFields = this.targetsByClass.get(context.type().getClassName());
        if (targetFields == null) {
            return ComputeFlags.NO_REWRITE;
        }

        boolean transformed = false;
        for (var field : context.node().fields) {
            if (targetFields.contains(field.name)) {
                transformer.transform(field, context);
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
