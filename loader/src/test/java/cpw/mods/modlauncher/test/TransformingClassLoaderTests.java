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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.classloading.JarContentsModule;
import net.neoforged.fml.classloading.JarContentsModuleFinder;
import net.neoforged.fml.classloading.JarMetadata;
import net.neoforged.fml.classloading.transformation.ClassProcessorAuditLog;
import net.neoforged.fml.classloading.transformation.ClassProcessorSet;
import net.neoforged.fml.classloading.transformation.TransformingClassLoader;
import net.neoforged.fml.jarcontents.JarContents;
import org.junit.jupiter.api.Test;

/**
 * Test class loader
 */
class TransformingClassLoaderTests {
    private static final String TARGET_CLASS = "cpw.mods.modlauncher.testjar.TestClass";

    @Test
    void testClassLoader() throws Exception {
        MockClassProcessor mockClassProcessor = new MockClassProcessor(TARGET_CLASS);

        var processorSet = ClassProcessorSet.builder()
                .addProcessor(mockClassProcessor)
                .build();

        Configuration configuration = createTestJarsConfiguration();
        TransformingClassLoader tcl = new TransformingClassLoader(processorSet, new ClassProcessorAuditLog(), configuration, List.of(ModuleLayer.boot()), null);
        ModuleLayer.boot().defineModules(configuration, s -> tcl);

        final Class<?> aClass = Class.forName(TARGET_CLASS, true, tcl);
        assertEquals(String.class, aClass.getField("testfield").getType());
        assertEquals("CHEESE!", aClass.getField("testfield").get(null));

        final Class<?> newClass = tcl.loadClass(TARGET_CLASS);
        assertEquals(aClass, newClass, "Class instance is the same from Class.forName and tcl.loadClass");
    }

    private Configuration createTestJarsConfiguration() throws IOException {
        Path path = Path.of(System.getProperty("testJars.location"));
        JarContents contents = JarContents.ofPath(path);
        JarContentsModule testJars = new JarContentsModule(
                contents,
                JarMetadata.from(contents).descriptor());
        var finder = new JarContentsModuleFinder(List.of(testJars));
        return ModuleLayer.boot().configuration().resolveAndBind(finder, ModuleFinder.ofSystem(), Set.of("cpw.mods.modlauncher.testjars"));
    }
}
