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

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Locale;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A transformer is injected into the modding ClassLoader. It can manipulate any item
 * it is designated to target.
 * <p>
 * {@link ITransformer}s must be named. FML will attempt to generate a name given the owner of the transformer
 * and its class name, but if more than one is provided that uses the same implementing class, it must override
 * {@link #name()}.
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
     * {@return a unique name for this transformer. Defaults to a name derived from the source class and mod file names}
     */
    default ProcessorName name() {
        return new ProcessorName(
                getOwnerName(getClass()),
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
        record ClassTarget(String className) implements Target {
            public ClassTarget {
                validateClassName(className);
            }
        }

        /**
         * Target a method.
         * 
         * @param className        the binary name of the class containing the method, as {@link Class#getName()}
         * @param methodName       the name of the method
         * @param methodDescriptor the method's descriptor string
         */
        record MethodTarget(String className, String methodName, String methodDescriptor) implements Target {
            public MethodTarget {
                validateClassName(className);
                validateMethod(methodName, methodDescriptor);
            }
        }

        /**
         * Target a field.
         * 
         * @param className the binary name of the class containing the field, as {@link Class#getName()}
         * @param fieldName the name of the field
         */
        record FieldTarget(String className, String fieldName) implements Target {
            public FieldTarget {
                validateClassName(className);
                validateUnqualified(fieldName);
            }
        }

        private static void validateClassName(String name) {
            ClassDesc.of(name);
        }

        private static void validateUnqualified(String name) {
            ".;[/<>".chars().forEach(c -> {
                if (name.indexOf(c) != -1) {
                    throw new IllegalArgumentException("Invalid unqualified name " + name);
                }
            });
        }

        private static void validateMethod(String name, String descriptor) {
            if (name.equals("<init>") || (name.equals("<clinit>") && descriptor.equals("()V"))) {
                return;
            }
            validateUnqualified(name);
            MethodTypeDesc.ofDescriptor(descriptor);
        }
    }

    private static String getOwnerName(Class<? extends ITransformer> clazz) {
        var module = clazz.getModule();
        if (module.isNamed()) {
            return module.getName();
        }
        var modFile = FMLLoader.getCurrent().getModFileByClass(clazz);
        if (modFile != null) {
            return modFile.getId();
        }
        throw new IllegalStateException("Cannot determine owner name for " + clazz + ", it is not in a named module and not loaded from a mod file");
    }
}
