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

import java.util.ArrayList;
import net.neoforged.fml.classloading.transformation.ClassHierarchyRecomputationContext;
import net.neoforged.fml.classloading.transformation.ClassProcessorAuditLog;
import net.neoforged.fml.classloading.transformation.ClassProcessorSet;
import net.neoforged.fml.classloading.transformation.ClassTransformer;
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

        var auditTrail = new ClassProcessorAuditLog();
        var classTransformer = new ClassTransformer(ClassProcessorSet.of(processor), auditTrail);
        assertThat(classTransformer.transform(new byte[0], "test.TestClass", null, mock(ClassHierarchyRecomputationContext.class)))
                .isEmpty();
        assertThat(handlesClassCalls).containsExactly(
                "empty=true, name=test.TestClass");
    }
}
