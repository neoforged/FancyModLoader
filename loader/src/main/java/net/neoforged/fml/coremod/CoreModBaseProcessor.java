/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.HashSet;
import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorMetadata;
import net.neoforged.neoforgespi.transformation.ProcessorName;

abstract class CoreModBaseProcessor implements ClassProcessor {
    private final Metadata metadata;

    public CoreModBaseProcessor(CoreModTransformer transformer) {
        var after = new HashSet<>(transformer.runsAfter());
        // coremod transformers always imply COMPUTE_FRAMES and thus must always run after it.
        after.add(ClassProcessorIds.COMPUTING_FRAMES);
        this.metadata = new Metadata(
                transformer.name(),
                Set.copyOf(transformer.runsBefore()),
                Set.copyOf(after));
    }

    @Override
    public ClassProcessorMetadata metadata() {
        return metadata;
    }

    private record Metadata(ProcessorName name, Set<ProcessorName> runsBefore,
            Set<ProcessorName> runsAfter) implements ClassProcessorMetadata {}
}
