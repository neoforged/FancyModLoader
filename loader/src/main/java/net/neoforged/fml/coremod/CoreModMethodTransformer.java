package net.neoforged.fml.coremod;

import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessorBehavior;
import org.objectweb.asm.tree.MethodNode;

non-sealed public interface CoreModMethodTransformer extends CoreModTransformer {
    /**
     * Transform the input with context.
     *
     * @param input   The ASM input node, which can be mutated directly
     * @param context The voting context
     */
    void transform(MethodNode input, CoreModTransformationContext context);

    Set<Target> targets();

    /**
     * Target a method.
     *
     * @param className        the binary name of the class containing the method, as {@link Class#getName()}
     * @param methodName       the name of the method
     * @param methodDescriptor the method's descriptor string
     */
    record Target(String className, String methodName, String methodDescriptor) {
        public Target {
            NameValidation.validateClassName(className);
            NameValidation.validateMethod(methodName, methodDescriptor);
        }
    }

    default ClassProcessorBehavior toClassProcessorBehavior() {
        return new CoreModMethodProcessor(this);
    }
}
