/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import org.objectweb.asm.tree.ClassNode;

non-sealed public interface CoreModClassTransformer extends CoreModTransformer {
    /**
     * Transform the input with context.
     *
     * @param input   The ASM input node, which can be mutated directly
     * @param context The voting context
     */
    void transform(ClassNode input, CoreModTransformationContext context);

    Set<Target> targets();

    /**
     * Target a class.
     *
     * @param className the binary name of the class, as {@link Class#getName()}
     */
    record Target(String className) {
        public Target {
            NameValidation.validateClassName(className);
        }
    }

    default ClassProcessor toProcessor() {
        return new CoreModClassProcessor(this);
    }
}
