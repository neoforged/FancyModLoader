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

import static org.junit.jupiter.api.Assertions.assertEquals;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Test class loader
 */
class TransformingClassLoaderTests {
    private static final String TARGET_CLASS = "cpw.mods.modlauncher.testjar.TestClass";

    @Test
    void testClassLoader() throws Exception {
        TransformStore transformStore = new TransformStore();
        transformStore.addTransformer(new MockTransformerService.ClassNodeTransformer(List.of(TARGET_CLASS)), "");
        LaunchPluginHandler lph = new LaunchPluginHandler(Stream.empty());
        var classTransformer = new ClassTransformer(transformStore, lph);

        Configuration configuration = createTestJarsConfiguration();
        TransformingClassLoader tcl = new TransformingClassLoader(classTransformer, configuration, List.of(ModuleLayer.boot()), null);
        ModuleLayer.boot().defineModules(configuration, s -> tcl);

        final Class<?> aClass = Class.forName(TARGET_CLASS, true, tcl);
        assertEquals(String.class, aClass.getField("testfield").getType());
        assertEquals("CHEESE!", aClass.getField("testfield").get(null));

        final Class<?> newClass = tcl.loadClass(TARGET_CLASS);
        assertEquals(aClass, newClass, "Class instance is the same from Class.forName and tcl.loadClass");
    }

    private Configuration createTestJarsConfiguration() throws IOException {
        SecureJar testJars = Jar.of(Path.of(System.getProperty("testJars.location")));
        JarModuleFinder finder = JarModuleFinder.of(testJars);
        return ModuleLayer.boot().configuration().resolveAndBind(finder, ModuleFinder.ofSystem(), Set.of("cpw.mods.modlauncher.testjars"));
    }
}
