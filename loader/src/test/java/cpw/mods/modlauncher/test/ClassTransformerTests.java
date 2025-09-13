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

package cpw.mods.modlauncher.test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.api.CoremodTransformationContext;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.CoreModsTransformerProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * Test core transformer functionality
 */
class ClassTransformerTests {
    @Disabled
    @Test
    void testClassTransformer() throws Exception {
        MarkerManager.getMarker("CLASSDUMP");
        Configurator.setLevel(ClassTransformer.class.getName(), Level.TRACE);
        ClassTransformer classTransformer = new ClassTransformer(new TransformStore(List.of(
                CoreModsTransformerProvider.makeTransformer(classTransformer(
                        ITransformer.Target.targetClass("test.MyClass"))))),
                null, null);
        byte[] result = classTransformer.transform(new byte[0], "test.MyClass", null);
        assertAll("Class loads and is valid",
                () -> assertNotNull(result),
//                () -> assertNotNull(new TransformingClassLoader(transformStore, lph, FileSystems.getDefault().getPath(".")).getClass("test.MyClass", result)),
                () -> {
                    ClassReader cr = new ClassReader(result);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("testfield")));
                });

        ClassNode dummyClass = new ClassNode();
        dummyClass.superName = "java/lang/Object";
        dummyClass.version = 52;
        dummyClass.name = "test/DummyClass";
        dummyClass.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "dummyfield", "Ljava/lang/String;", null, null));
        ClassWriter cw = new ClassWriter(Opcodes.ASM5);
        dummyClass.accept(cw);
        classTransformer = new ClassTransformer(new TransformStore(List.of(
                CoreModsTransformerProvider.makeTransformer(fieldNodeTransformer1(
                        ITransformer.Target.targetField("test.DummyClass", "dummyfield"))))),
                null, null);
        byte[] result1 = classTransformer.transform(cw.toByteArray(), "test.DummyClass", null);
        assertAll("Class loads and is valid",
                () -> assertNotNull(result1),
//                () -> assertNotNull(new TransformingClassLoader(transformStore, lph, FileSystems.getDefault().getPath(".")).getClass("test.DummyClass", result1)),
                () -> {
                    ClassReader cr = new ClassReader(result1);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    assertEquals("CHEESE", cn.fields.get(0).value);
                });
    }

    private ITransformer<FieldNode> fieldNodeTransformer1(ITransformer.Target<FieldNode> target) {
        return new ITransformer<>() {
            @Override
            public void transform(FieldNode input, CoremodTransformationContext context) {
                input.value = "CHEESE";
            }

            @Override
            public Set<Target<FieldNode>> targets() {
                return Set.of(target);
            }

            @Override
            public TargetType getTargetType() {
                return TargetType.FIELD;
            }
        };
    }

    private ITransformer<ClassNode> classTransformer(ITransformer.Target<ClassNode> target) {
        return new ITransformer<>() {
            @Override
            public void transform(ClassNode input, CoremodTransformationContext context) {
                input.superName = "java/lang/Object";
                FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC, "testfield", "Ljava/lang/String;", null, null);
                input.fields.add(fn);
            }

            @Override
            public Set<Target<ClassNode>> targets() {
                return Set.of(target);
            }

            @Override
            public TargetType getTargetType() {
                return TargetType.CLASS;
            }
        };
    }
}
