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

package cpw.mods.modlauncher.benchmarks;

import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformerAuditTrail;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class TransformBenchmark {
    public volatile ClassTransformer classTransformer;
    byte[] classBytes;

    @Setup
    public void setup() throws Exception {
        final TransformStore transformStore = new TransformStore();
        try (var is = getClass().getClassLoader().getResourceAsStream("cpw/mods/modlauncher/testjar/TestClass.class")) {
            classBytes = is.readAllBytes();
        }
        var plugin = new ILaunchPluginService() {
            @Override
            public String name() {
                return "dummy1";
            }

            @Override
            public boolean processClass(final Phase phase, final ClassNode classNode, final Type classType) {
                return true;
            }

            @Override
            public <T> T getExtension() {
                return null;
            }

            @Override
            public EnumSet<Phase> handlesClass(final Type classType, final boolean isEmpty) {
                return EnumSet.of(Phase.BEFORE, Phase.AFTER);
            }
        };
        final LaunchPluginHandler lph = new LaunchPluginHandler(Stream.of(plugin));
        classTransformer = new ClassTransformer(transformStore, lph);
    }

    @Benchmark
    public int transformNoop() {
        byte[] result = classTransformer.transform(null, new byte[0], "test.MyClass", "jmh");
        return result.length + 1;
    }

    @TearDown(Level.Iteration)
    public void clearLog() {
        TransformerAuditTrail auditTrail = classTransformer.getAuditTrail();
        auditTrail.clear();
    }

    @Benchmark
    public int transformDummyClass() {
        byte[] result = classTransformer.transform(null, classBytes, "cpw.mods.modlauncher.testjar.TestClass", "jmh");
        return result.length + 1;
    }
}
