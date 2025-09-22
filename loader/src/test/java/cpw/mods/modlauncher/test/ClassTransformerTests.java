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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cpw.mods.modlauncher.ClassHierarchyRecomputationContext;
import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformerAuditTrail;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Test core transformer functionality
 */
@MockitoSettings
class ClassTransformerTests {
    @Test
    void testClassTransformer() throws Exception {
        var handlesClassCalls = new ArrayList<String>();
        var processor = new ClassProcessor() {
            @Override
            public ProcessorName name() {
                return ProcessorName.parse("test:test");
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                handlesClassCalls.add("empty=" + context.empty() + ", name=" + context.type().getClassName());
                return false;
            }

            @Override
            public ComputeFlags processClass(TransformationContext context) {
                return Assertions.fail();
            }
        };

        var auditTrail = new TransformerAuditTrail();
        var store = new TransformStore(List.of(processor));
        var classTransformer = new ClassTransformer(store, auditTrail);
        assertThat(classTransformer.transform(new byte[0], "test.TestClass", null, mock(ClassHierarchyRecomputationContext.class)))
                .isEmpty();
        assertThat(handlesClassCalls).containsExactly(
                "empty=true, name=test.TestClass");
    }
}
