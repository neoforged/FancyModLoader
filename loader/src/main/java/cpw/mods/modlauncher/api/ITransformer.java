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

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A transformer is injected into the modding ClassLoader. It can manipulate any item
 * it is designated to target.
 */
public sealed interface ITransformer {
    ProcessorName COREMODS_GROUP = new ProcessorName("neoforge", "coremods_default");

    non-sealed interface ClassTransformer extends ITransformer {
        /**
         * Transform the input with context.
         *
         * @param input   The ASM input node, which can be mutated directly
         * @param context The voting context
         */
        void transform(ClassNode input, CoreModTransformationContext context);

        @Override
        Set<Target.ClassTarget> targets();
    }

    non-sealed interface MethodTransformer extends ITransformer {
        /**
         * Transform the input with context.
         *
         * @param input   The ASM input node, which can be mutated directly
         * @param context The voting context
         */
        void transform(MethodNode input, CoreModTransformationContext context);

        @Override
        Set<Target.MethodTarget> targets();
    }

    non-sealed interface FieldTransformer extends ITransformer {
        /**
         * Transform the input with context.
         *
         * @param input   The ASM input node, which can be mutated directly
         * @param context The voting context
         */
        void transform(FieldNode input, CoreModTransformationContext context);

        @Override
        Set<Target.FieldTarget> targets();
    }

    /**
     * Return a set of {@link Target} identifying which elements this transformer wishes to try
     * and apply to.
     *
     * @return The set of targets this transformer wishes to apply to
     */

    Set<? extends Target> targets();

    /**
     * {@return a unique name for this transformer. Defaults to a name derived from the source class and module names}
     */
    default ProcessorName name() {
        return new ProcessorName(
                Objects.requireNonNull(getClass().getModule().getName(), "coremod must be in named module or have explicit name"),
                getClass().getName().replace('$', '.').toLowerCase(Locale.ROOT));
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
     * Indicates where the {@link ITransformer} can target.
     */
    interface Target {
        /**
         * {@return the binary name of the class being targeted, as {@link Class#getName()}}
         */
        String className();

        /**
         * Target a class.
         * 
         * @param className the binary name of the class, as {@link Class#getName()}
         */
        record ClassTarget(String className) implements Target {}

        /**
         * Target a method.
         * 
         * @param className        the binary name of the class containing the method, as {@link Class#getName()}
         * @param methodName       the name of the method
         * @param methodDescriptor the method's descriptor string
         */
        record MethodTarget(String className, String methodName, String methodDescriptor) implements Target {}

        /**
         * Target a field.
         * 
         * @param className the binary name of the class containing the field, as {@link Class#getName()}
         * @param fieldName the name of the field
         */
        record FieldTarget(String className, String fieldName) implements Target {}
    }
}
