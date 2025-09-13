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

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.TypesafeMap;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Test class loader
 */
class TransformingClassLoaderTests {
    private static final String TARGET_CLASS = "cpw.mods.modlauncher.testjar.TestClass";

    @Test
    void testClassLoader() throws Exception {
        MockClassProcessor mockClassProcessor = new MockClassProcessor(TARGET_CLASS);

        TransformStore transformStore = new TransformStore(List.of(mockClassProcessor));

        Environment environment = new Environment(null);
        new TypesafeMap(IEnvironment.class);
        Configuration configuration = createTestJarsConfiguration();
        TransformingClassLoader tcl = new TransformingClassLoader(transformStore, environment, configuration, List.of(ModuleLayer.boot()));
        ModuleLayer.boot().defineModules(configuration, s -> tcl);

        final Class<?> aClass = Class.forName(TARGET_CLASS, true, tcl);
        assertEquals(String.class, aClass.getField("testfield").getType());
        assertEquals("CHEESE!", aClass.getField("testfield").get(null));

        final Class<?> newClass = tcl.loadClass(TARGET_CLASS);
        assertEquals(aClass, newClass, "Class instance is the same from Class.forName and tcl.loadClass");
    }

    private Configuration createTestJarsConfiguration() throws IOException {
        SecureJar testJars = SecureJar.from(Path.of(System.getProperty("testJars.location")));
        JarModuleFinder finder = JarModuleFinder.of(testJars);
        return ModuleLayer.boot().configuration().resolveAndBind(finder, ModuleFinder.ofSystem(), Set.of("cpw.mods.modlauncher.testjars"));
    }
}
