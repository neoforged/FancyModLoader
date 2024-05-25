package net.neoforged.fml.common.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public class ListGeneratorAdapter extends GeneratorAdapter {
    public ListGeneratorAdapter(InsnList insnList) {
        super(Opcodes.ASM9, null, 0, "", "()V");
        MethodNode method = new MethodNode();
        method.instructions = insnList;
        mv = method;
    }
}
