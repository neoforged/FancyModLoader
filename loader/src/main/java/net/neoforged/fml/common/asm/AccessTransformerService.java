/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import java.util.Set;

import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.neoforgespi.transformation.IClassProcessor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class AccessTransformerService implements IClassProcessor {
    public final AccessTransformerEngine engine = AccessTransformerEngine.newEngine();

    @Override
    public String name() {
        return "accesstransformer";
    }

    @Override
    public Set<String> runsBefore() {
        return Set.of("mixin");
    }

    @Override
    public int processClassWithFlags(final ClassNode classNode, final Type classType) {
        return engine.transform(classNode, classType) ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
    }

    @Override
    public boolean handlesClass(final Type classType, final boolean isEmpty) {
        return !isEmpty && engine.getTargets().contains(classType);
    }
}
