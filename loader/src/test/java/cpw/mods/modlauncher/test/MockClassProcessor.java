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

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorMetadata;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

/**
 * Test Launcher Service
 */
public class MockClassProcessor implements ClassProcessor {
    private final String name;
    private final ClassProcessorMetadata metadata;

    public MockClassProcessor(String name) {
        this.name = name;
        this.metadata = new ClassProcessorMetadata() {
            @Override
            public ProcessorName name() {
                return new ProcessorName("test", "test");
            }
        };
    }

    @Override
    public ClassProcessorMetadata metadata() {
        return metadata;
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return context.type().getClassName().equals(this.name);
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "testfield", "Ljava/lang/String;", null, "CHEESE!");
        context.node().fields.add(fn);
        return ComputeFlags.COMPUTE_FRAMES;
    }
}
