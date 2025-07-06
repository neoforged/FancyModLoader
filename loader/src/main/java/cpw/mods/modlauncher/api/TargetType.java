package cpw.mods.modlauncher.api;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Specifies the target type for the {@link ITransformer.Target}.
 */
public enum TargetType {
    CLASS,
    METHOD,
    FIELD
}
