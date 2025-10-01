/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessorBehavior;
import org.objectweb.asm.tree.FieldNode;

non-sealed public interface CoreModFieldTransformer extends CoreModTransformer {
    /**
     * Transform the input with context.
     *
     * @param input   The ASM input node, which can be mutated directly
     * @param context The voting context
     */
    void transform(FieldNode input, CoreModTransformationContext context);

    Set<Target> targets();

    /**
     * Target a field.
     *
     * @param className the binary name of the class containing the field, as {@link Class#getName()}
     * @param fieldName the name of the field
     */
    record Target(String className, String fieldName) {
        public Target {
            NameValidation.validateClassName(className);
            NameValidation.validateUnqualified(fieldName);
        }
    }

    default ClassProcessorBehavior toClassProcessorBehavior() {
        return new CoreModFieldProcessor(this);
    }
}
