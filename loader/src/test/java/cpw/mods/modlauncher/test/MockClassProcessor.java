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
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

/**
 * Test Launcher Service
 */
public class MockClassProcessor implements ClassProcessor {
    private final String name;

    public MockClassProcessor(String name) {
        this.name = name;
    }

    @Override
    public ProcessorName name() {
        return new ProcessorName("test", "test");
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return context.type().getClassName().equals(this.name);
    }

    @Override
    public boolean processClass(TransformationContext context) {
        FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "testfield", "Ljava/lang/String;", null, "CHEESE!");
        context.node().fields.add(fn);
        return true;
    }
}
