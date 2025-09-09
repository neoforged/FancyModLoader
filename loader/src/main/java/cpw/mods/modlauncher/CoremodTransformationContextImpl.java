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

package cpw.mods.modlauncher;

import cpw.mods.modlauncher.api.CoremodTransformationContext;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The internal vote context structure.
 */
@ApiStatus.Internal
public class CoremodTransformationContextImpl implements CoremodTransformationContext {
    private static final Object[] EMPTY = new Object[0];
    private final ClassProcessor.TransformationContext context;
    private final Object node;

    public CoremodTransformationContextImpl(ClassProcessor.TransformationContext context, Object node) {
        this.context = context;
        this.node = node;
    }

    @Override
    public String getClassName() {
        return context.type().getClassName();
    }

    @Override
    public boolean doesClassExist() {
        return !context.empty();
    }

    @Override
    public byte[] getInitialClassSha256() {
        return context.initialSha256();
    }

    @Override
    public boolean applyFieldPredicate(FieldPredicate fieldPredicate) {
        FieldNode fn = (FieldNode) this.node;
        final PredicateVisitor predicateVisitor = new PredicateVisitor(fieldPredicate);
        fn.accept(predicateVisitor);
        return predicateVisitor.getResult();
    }

    @Override
    public boolean applyMethodPredicate(MethodPredicate methodPredicate) {
        MethodNode mn = (MethodNode) this.node;
        final PredicateVisitor predicateVisitor = new PredicateVisitor(methodPredicate);
        mn.accept(predicateVisitor);
        return predicateVisitor.getResult();
    }

    @Override
    public boolean applyClassPredicate(ClassPredicate classPredicate) {
        ClassNode cn = (ClassNode) this.node;
        final PredicateVisitor predicateVisitor = new PredicateVisitor(classPredicate);
        cn.accept(predicateVisitor);
        return predicateVisitor.getResult();
    }

    @Override
    public boolean applyInstructionPredicate(InsnPredicate insnPredicate) {
        MethodNode mn = (MethodNode) this.node;
        boolean result = false;
        final AbstractInsnNode[] insnNodes = mn.instructions.toArray();
        for (int i = 0; i < insnNodes.length; i++) {
            result |= insnPredicate.test(i, insnNodes[i].getOpcode(), toObjectArray(insnNodes[0]));
        }
        return result;
    }

    private Object[] toObjectArray(final AbstractInsnNode insnNode) {
        if (insnNode instanceof MethodInsnNode methodInsnNode) {
            return new Object[] { methodInsnNode.name, methodInsnNode.desc, methodInsnNode.owner, methodInsnNode.itf };
        }
        if (insnNode instanceof FieldInsnNode fieldInsnNode) {
            return new Object[] { fieldInsnNode.name, fieldInsnNode.desc, fieldInsnNode.owner };
        }
        return EMPTY;
    }
}
