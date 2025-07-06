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

import cpw.mods.modlauncher.api.ITransformationContext;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class PredicateVisitor extends ClassVisitor {
    private static final int ASM_API = Opcodes.ASM9;

    private ITransformationContext.MethodPredicate methodPredicate;
    private ITransformationContext.FieldPredicate fieldPredicate;
    private ITransformationContext.ClassPredicate classPredicate;
    private boolean result;

    PredicateVisitor(final ITransformationContext.FieldPredicate fieldPredicate) {
        super(ASM_API);
        this.fieldPredicate = fieldPredicate;
    }

    PredicateVisitor(final ITransformationContext.MethodPredicate methodPredicate) {
        super(ASM_API);
        this.methodPredicate = methodPredicate;
    }

    PredicateVisitor(final ITransformationContext.ClassPredicate classPredicate) {
        super(ASM_API);
        this.classPredicate = classPredicate;
    }

    boolean getResult() {
        return result;
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
        result = fieldPredicate == null || fieldPredicate.test(access, name, descriptor, signature, value);
        return null;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
        result = methodPredicate == null || methodPredicate.test(access, name, descriptor, signature, exceptions);
        return null;
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
        result = classPredicate == null || classPredicate.test(version, access, name, signature, superName, interfaces);
    }
}
