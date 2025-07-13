/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.Config;
import java.lang.reflect.Modifier;
import java.util.List;
import net.neoforged.neoforgespi.locating.IModFile;
import org.junit.jupiter.api.Test;

public class AccessTransformerTest extends LauncherTest {
    /**
     * Just a testmod that makes one of its own classes public using an AT at the default location.
     */
    @Test
    void testModWithDefaultAccessTransformer() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("test.jar")
                .withTestmodModsToml()
                .addClass("testmod.TestClass", """
                        class TestClass {}
                        """)
                .addTextFile("META-INF/accesstransformer.cfg", "public testmod.TestClass")
                .build();

        launchAndLoad("neoforgeclient");

        assertClassIsPublic("testmod.TestClass");
    }

    /**
     * Just a testmod that makes one of its own classes public using an AT at a custom location, and
     * check that the default AT is then *not* loaded.
     */
    @Test
    void testModWithExplicitAccessTransformer() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("test.jar")
                .withTestmodModsToml(toml -> toml.customize(config -> {
                    var atEntry = Config.inMemory();
                    atEntry.set("file", "customat.cfg");
                    config.add("accessTransformers", List.of(atEntry));
                }))
                .addClass("testmod.TestClass", """
                        class TestClass {}
                        """)
                .addClass("testmod.TestClass2", """
                        class TestClass2 {}
                        """)
                // This should NOT be applied!
                .addTextFile("META-INF/accesstransformer.cfg", "public testmod.TestClass2")
                // This should NOT be applied!
                .addTextFile("customat.cfg", "public testmod.TestClass")
                .build();

        launchAndLoad("neoforgeclient");

        assertClassIsPublic("testmod.TestClass");

        var testClass2 = Class.forName("testmod.TestClass2", false, gameClassLoader);
        assertEquals(0, testClass2.getModifiers(), "Expected the default AT not to be applied, but it was.");
    }

    /**
     * This tests that gamelibraries cannot apply ATs by putting them in the default location.
     */
    @Test
    void testGameLibraryWithDefaultAccessTransformer() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("testlib.jar")
                .withModTypeManifest(IModFile.Type.GAMELIBRARY.name())
                .addClass("testlib.TestClass", """
                        class TestClass {}
                        """)
                .addTextFile("META-INF/accesstransformer.cfg", "public testlib.TestClass")
                .build();

        var result = launchAndLoad("neoforgeclient");
        assertThat(result.gameLayerModules()).containsKey("testlib");

        var clazz = Class.forName("testlib.TestClass", false, gameClassLoader);
        assertEquals(0, clazz.getModifiers(), "Expected the default AT not to be applied for gamelibraries.");
    }

    private void assertClassIsPublic(String className) throws Exception {
        var clazz = Class.forName(className, false, gameClassLoader);
        assertTrue(Modifier.isPublic(clazz.getModifiers()));
    }
}
