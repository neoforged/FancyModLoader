/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class AccessTransformerService implements ILaunchPluginService {
    public final AccessTransformerEngine engine = AccessTransformerEngine.newEngine();

    @Override
    public String name() {
        return "accesstransformer";
    }

    @Override
    public int processClassWithFlags(final Phase phase, final ClassNode classNode, final Type classType, final String reason) {
        return engine.transform(classNode, classType) ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
    }

    private static final EnumSet<Phase> YAY = EnumSet.of(Phase.BEFORE);
    private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

    @Override
    public EnumSet<Phase> handlesClass(final Type classType, final boolean isEmpty) {
        return !isEmpty && engine.getTargets().contains(classType) ? YAY : NAY;
    }
}
