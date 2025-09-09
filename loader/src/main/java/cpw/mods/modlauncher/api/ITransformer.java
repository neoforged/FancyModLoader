/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher.api;

import java.util.Set;

import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A transformer is injected into the modding ClassLoader. It can manipulate any item
 * it is designated to target.
 */
public interface ITransformer<T> {
    ProcessorName COREMODS_GROUP = new ProcessorName("neoforge", "coremods_default");
    
    /**
     * Transform the input to the ITransformer's desire. The context from the last vote is
     * provided as well.
     *
     * @param input   The ASM input node, which can be mutated directly
     * @param context The voting context
     */
    void transform(T input, CoremodTransformationContext context);

/**
     * Return a set of {@link Target} identifying which elements this transformer wishes to try
     * and apply to. The {@link Target#getTargetType()} must match the T variable for the transformer
     * as documented in {@link TargetType}, other combinations will be rejected.
     *
     * @return The set of targets this transformer wishes to apply to
     */

    Set<Target<T>> targets();

    TargetType getTargetType();
    
    /** 
     * {@return a unique name for this transformer. Defaults to a name derived from the source class and module names}
     */
    default ProcessorName name() {
        return new ProcessorName(getClass().getModule().getName(), getClass().getName().replace('$', '.'));
    }

    /**
     * {@return processors or transformers that this transformer must run before}
     */
    default Set<ProcessorName> runsBefore() {
        return Set.of();
    }
    
    /**
     * {@return processors or transformers that this transformer must run after} Defaults to running after {@link ITransformer#COREMODS_GROUP}, which runs after mixins.
     */
    default Set<ProcessorName> runsAfter() {
        return Set.of(ITransformer.COREMODS_GROUP);
    }

    /**
     * Simple data holder indicating where the {@link ITransformer} can target.
     * 
     * @param className         The binary name of the class being targetted, as {@link Class#getName()}
     * @param elementName       The name of the element being targetted. This is the field name for a field,
     *                          the method name for a method. Empty string for other types
     * @param elementDescriptor The method's descriptor. Empty string for other types
     * @param targetType        The {@link TargetType} for this target - it should match the ITransformer
     *                          type variable T
     */
    record Target<T>(String className, String elementName, String elementDescriptor, TargetType targetType) {
        /**
         * Convenience method returning a {@link Target} for a class
         *
         * @param className The binary name of the class, as {@link Class#getName()}
         * @return A target for the named class
         */

        public static Target<ClassNode> targetClass(String className) {
            return new Target<>(className, "", "", TargetType.CLASS);
        }

        /**
         * Convenience method return a {@link Target} for a method
         *
         * @param className        The binary name of the class containing the method, as {@link Class#getName()}
         * @param methodName       The name of the method
         * @param methodDescriptor The method's descriptor string
         * @return A target for the named method
         */

        public static Target<MethodNode> targetMethod(String className, String methodName, String methodDescriptor) {
            return new Target<>(className, methodName, methodDescriptor, TargetType.METHOD);
        }

        /**
         * Convenience method returning a {@link Target} for a field
         *
         * @param className The binary name of the class containing the field, as {@link Class#getName()}
         * @param fieldName The name of the field
         * @return A target for the named field
         */

        public static Target<FieldNode> targetField(String className, String fieldName) {
            return new Target<>(className, fieldName, "", TargetType.FIELD);
        }
    }
}
