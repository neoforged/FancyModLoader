/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod.processor;

import java.util.HashSet;
import java.util.Set;
import net.neoforged.fml.coremod.CoreModTransformer;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

public abstract class CoreModBaseProcessor implements ClassProcessor {
    private final ProcessorName name;
    private final Set<ProcessorName> runsBefore;
    private final Set<ProcessorName> runsAfter;

    public CoreModBaseProcessor(CoreModTransformer transformer) {
        var after = new HashSet<>(transformer.runsAfter());
        // coremod transformers always imply COMPUTE_FRAMES and thus must always run after it.
        after.add(ClassProcessorIds.COMPUTING_FRAMES);

        this.name = transformer.name();
        this.runsBefore = Set.copyOf(transformer.runsBefore());
        this.runsAfter = Set.copyOf(after);
    }

    @Override
    public ProcessorName name() {
        return name;
    }

    @Override
    public Set<ProcessorName> runsBefore() {
        return runsBefore;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return runsAfter;
    }
}
