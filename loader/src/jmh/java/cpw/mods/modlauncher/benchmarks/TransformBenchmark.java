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
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformerAuditTrail;
import java.io.InputStream;
import java.util.List;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class TransformBenchmark {
    public volatile ClassTransformer classTransformer;
    public volatile TransformerAuditTrail auditTrail;
    byte[] classBytes;

    @Setup
    public void setup() throws Exception {
        final TransformStore transformStore = new TransformStore(List.of(
                new ClassProcessor() {
                    @Override
                    public ProcessorName name() {
                        return new ProcessorName("benchmark", "dummy1");
                    }

                    @Override
                    public boolean handlesClass(SelectionContext context) {
                        return true;
                    }

                    @Override
                    public ComputeFlags processClass(TransformationContext context) {
                        return ComputeFlags.COMPUTE_FRAMES;
                    }
                }));
        auditTrail = new TransformerAuditTrail();
        classTransformer = new ClassTransformer(transformStore, null, auditTrail, null);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("cpw/mods/modlauncher/testjar/TestClass.class")) {
            classBytes = is.readAllBytes();
        }
    }

    @Benchmark
    public int transformNoop() {
        byte[] result = classTransformer.transform(new byte[0], "test.MyClass", null);
        return result.length + 1;
    }

    @TearDown(Level.Iteration)
    public void clearLog() {
        auditTrail.clear();
    }

    @Benchmark
    public int transformDummyClass() {
        byte[] result = classTransformer.transform(classBytes, "cpw.mods.modlauncher.testjar.TestClass", null);
        return result.length + 1;
    }
}
