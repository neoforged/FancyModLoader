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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeEach;
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
    private ClassTransformer classTransformer;
    private TransformStore transformStore;

    @BeforeEach
    void setup() {
        MarkerManager.getMarker("CLASSDUMP");
        Configurator.setLevel(ClassTransformer.class.getName(), Level.TRACE);
        transformStore = new TransformStore();
        classTransformer = new ClassTransformer(transformStore, new LaunchPluginHandler(Stream.empty()), new TransformerAuditTrail());
    }

    @Test
    void testClassTransformer() {
        transformStore.addTransformer(classTransformer(), "test");
        byte[] result = classTransformer.transform(null, new byte[0], "test.MyClass", "testing");
        assertOnClassNode(result, cn -> {
            assertThat(cn.fields).extracting(f -> f.name).containsOnly("testfield");
        });
    }

    @Test
    void testFieldTransformer() {
        ClassNode dummyClass = new ClassNode();
        dummyClass.superName = "java/lang/Object";
        dummyClass.version = 52;
        dummyClass.name = "test/DummyClass";
        dummyClass.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "dummyfield", "Ljava/lang/String;", null, null));
        ClassWriter cw = new ClassWriter(Opcodes.ASM5);
        dummyClass.accept(cw);
        transformStore.addTransformer(fieldNodeTransformer1(), "test");
        byte[] result1 = classTransformer.transform(null, cw.toByteArray(), "test.DummyClass", "testing");
        assertOnClassNode(result1, cn -> assertEquals("CHEESE", cn.fields.getFirst().value));
    }

    private static void assertOnClassNode(byte[] bytecode, Consumer<ClassNode> asserter) {
        ClassReader cr = new ClassReader(bytecode);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        asserter.accept(cn);
    }

    private ITransformer<FieldNode> fieldNodeTransformer1() {
        return new ITransformer<>() {
            @Override
            public FieldNode transform(FieldNode input, ITransformerVotingContext context) {
                input.value = "CHEESE";
                return input;
            }

            @Override
            public TransformerVoteResult castVote(ITransformerVotingContext context) {
                return TransformerVoteResult.YES;
            }

            @Override
            public Set<Target<FieldNode>> targets() {
                return Set.of(Target.targetField("test.DummyClass", "dummyfield", "Ljava/lang/String;"));
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
            public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
                input.superName = "java/lang/Object";
                FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC, "testfield", "Ljava/lang/String;", null, null);
                input.fields.add(fn);
                return input;
            }

            @Override
            public TransformerVoteResult castVote(ITransformerVotingContext context) {
                return TransformerVoteResult.YES;
            }

            @Override
            public Set<Target<ClassNode>> targets() {
                return Set.of(Target.targetClass("test.MyClass"));
            }

            @Override
            public TargetType<ClassNode> getTargetType() {
                return TargetType.CLASS;
            }
        };
    }
}
