package cpw.mods.modlauncher;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.Nullable;

/**
 * Provided the {@link ClassTransformer#transform(byte[], String, ProcessorName, ClassHierarchyRecomputationContext)}
 * when transforming a class; allows for recomputing the class hierarchy if needed (see logic in {@link TransformerClassWriter}.
 * <p>
 * All class names used here are in the standard Java form (dot-separated), as in the rest of the {@link ClassProcessor} system
 */
public interface ClassHierarchyRecomputationContext {
    @Nullable
    Class<?> findLoadedClass(String name);

    byte[] upToFrames(String className) throws ClassNotFoundException;

    Class<?> locateParentClass(String className) throws ClassNotFoundException;
}
