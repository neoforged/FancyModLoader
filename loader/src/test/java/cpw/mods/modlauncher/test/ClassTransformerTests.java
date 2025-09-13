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
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformTargetLabel;
import cpw.mods.modlauncher.api.CoremodTransformationContext;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;
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
        final TransformStore transformStore = new TransformStore();
        final LaunchPluginHandler lph = new LaunchPluginHandler(Stream.of());
        final ClassTransformer classTransformer = new ClassTransformer(transformStore, lph, null);
        final ITransformationService dummyService = new MockTransformerService();
        transformStore.addTransformer(new TransformTargetLabel("test.MyClass", TargetType.CLASS), classTransformer(), dummyService);
        byte[] result = classTransformer.transform(new byte[0], "test.MyClass", "testing");
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
        transformStore.addTransformer(new TransformTargetLabel("test.DummyClass", "dummyfield"), fieldNodeTransformer1(), dummyService);
        byte[] result1 = classTransformer.transform(cw.toByteArray(), "test.DummyClass", "testing");
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

    private ITransformer<FieldNode> fieldNodeTransformer1() {
        return new ITransformer<>() {
            @Override
            public FieldNode transform(FieldNode input, CoremodTransformationContext context) {
                input.value = "CHEESE";
                return input;
            }

            @Override
            public TransformerVoteResult castVote(CoremodTransformationContext context) {
                return TransformerVoteResult.YES;
            }

            @Override
            public Set<Target<FieldNode>> targets() {
                return Collections.emptySet();
            }

            @Override
            public TargetType<FieldNode> getTargetType() {
                return TargetType.FIELD;
            }
        };
    }

    private ITransformer<ClassNode> classTransformer() {
        return new ITransformer<>() {
            @Override
            public ClassNode transform(ClassNode input, CoremodTransformationContext context) {
                input.superName = "java/lang/Object";
                FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC, "testfield", "Ljava/lang/String;", null, null);
                input.fields.add(fn);
                return input;
            }

            @Override
            public TransformerVoteResult castVote(CoremodTransformationContext context) {
                return TransformerVoteResult.YES;
            }

            @Override
            public Set<Target<ClassNode>> targets() {
                return Collections.emptySet();
            }

            @Override
            public TargetType<ClassNode> getTargetType() {
                return TargetType.CLASS;
            }
        };
    }
}
