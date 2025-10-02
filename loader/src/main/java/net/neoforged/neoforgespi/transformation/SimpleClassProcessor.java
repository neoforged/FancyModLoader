/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.objectweb.asm.tree.ClassNode;

public abstract non-sealed class SimpleClassProcessor extends BaseSimpleProcessor {
    /**
     * Transform the input with context.
     *
     * @param input   The ASM input node, which can be mutated directly
     * @param context The voting context
     */
    public abstract void transform(ClassNode input, SimpleTransformationContext context);

    public abstract Set<Target> targets();

    /**
     * Target a class.
     *
     * @param className the binary name of the class, as {@link Class#getName()}
     */
    public record Target(String className) {
        public Target {
            NameValidation.validateClassName(className);
        }
    }

    private final AtomicReference<Set<String>> targets = new AtomicReference<>();

    @Override
    public final boolean handlesClass(SelectionContext context) {
        var targets = this.targets.updateAndGet(
                set -> set != null ? set : targets().stream().map(Target::className).collect(Collectors.toSet()));
        return targets.contains(context.type().getClassName());
    }

    @Override
    public final ComputeFlags processClass(TransformationContext context) {
        transform(context.node(), context);
        return ComputeFlags.COMPUTE_FRAMES;
    }
}
