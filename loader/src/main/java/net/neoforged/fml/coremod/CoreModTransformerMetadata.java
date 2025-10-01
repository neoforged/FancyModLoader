/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.HashSet;
import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorMetadata;
import net.neoforged.neoforgespi.transformation.ProcessorName;

record CoreModTransformerMetadata(ProcessorName name, Set<ProcessorName> runsBefore,
        Set<ProcessorName> runsAfter) implements ClassProcessorMetadata {
    static CoreModTransformerMetadata of(CoreModTransformer transformer) {
        var after = new HashSet<>(transformer.runsAfter());
        // coremod transformers always imply COMPUTE_FRAMES and thus must always run after it.
        after.add(ClassProcessor.COMPUTING_FRAMES);
        return new CoreModTransformerMetadata(
                transformer.name(),
                Set.copyOf(transformer.runsBefore()),
                Set.copyOf(after));
    }
}
