/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.asm;

import java.nio.charset.StandardCharsets;
import net.neoforged.fml.loading.IdentifiableContent;
import net.neoforged.fml.loading.LauncherTest;
import net.neoforged.fml.loading.SimulatedInstallation;
import net.neoforged.fml.test.RuntimeCompiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// FIXME: doesn't actually test anything because there's no transforming classloader (see commented-out code in test mod class)
public class RuntimeEnumExtensionTest extends LauncherTest {
    @Test
    public void testEnumExtension() throws Exception {
        installation.setupProductionClient();

        var testJar = installation.writeModJar(
                "enum_ext_test.jar",
                SimulatedInstallation.createModsToml("enumtestmod", "1.0"),
                new IdentifiableContent("enumtestmod_ENUM_EXT_DATA", "META-INF/enumextender.json", """
                        {
                            "modid": "enumtestmod",
                            "entries": [
                                {
                                    "enum": "enumtestmod/ExtensibleEnum",
                                    "name": "OTHER_TEST_THING",
                                    "constructor": "(Ljava/lang/String;)V",
                                    "parameters": [ "enumtestmod:other_test_thing" ]
                                },
                                {
                                    "enum": "enumtestmod/ExtensibleEnum",
                                    "name": "ENUMTESTMOD_PREFIXED_TEST_THING",
                                    "constructor": "(Ljava/lang/String;)V",
                                    "parameters": [ "enumtestmod:prefixed_test_thing" ]
                                },
                                {
                                    "enum": "enumtestmod/ExtensibleEnum",
                                    "name": "ENUMTESTMOD_LAZY_TEST_THING",
                                    "constructor": "(Ljava/lang/String;)V",
                                    "parameters": {
                                        "class": "enumtestmod/TestMod",
                                        "field": "ENUM_PARAMS"
                                    }
                                },
                                {
                                    "enum": "enumtestmod/EnumWithId",
                                    "name": "OTHER_ID_TEST_THING",
                                    "constructor": "(ILjava/lang/String;)V",
                                    "parameters": [ -1, "enumtestmod:other_id_test_thing" ]
                                },
                                {
                                    "enum": "enumtestmod/EnumWithId",
                                    "name": "ENUMTESTMOD_PREFIXED_ID_TEST_THING",
                                    "constructor": "(ILjava/lang/String;)V",
                                    "parameters": [ -1, "enumtestmod:prefixed_id_test_thing" ]
                                },
                                {
                                    "enum": "enumtestmod/EnumWithId",
                                    "name": "ENUMTESTMOD_LAZY_ID_TEST_THING",
                                    "constructor": "(ILjava/lang/String;)V",
                                    "parameters": {
                                        "class": "enumtestmod/TestMod",
                                        "field": "ID_ENUM_PARAMS"
                                    }
                                }
                            ]
                        }
                        """.getBytes(StandardCharsets.UTF_8)));
        try (var compiler = RuntimeCompiler.create(testJar)) {
            compiler.builder()
                    .addClass("enumtestmod.ExtensibleEnum", """
                            package enumtestmod;
                            public enum ExtensibleEnum implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
                                TEST_THING("test");
                            
                                private final String name;
                                ExtensibleEnum(String name) {
                                    this.name = name;
                                }
                            }
                            """)
                    .addClass("enumtestmod.EnumWithId", """
                            package enumtestmod;
                            @net.neoforged.fml.common.asm.enumextension.NumberedEnum
                            public enum EnumWithId implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
                                TEST_THING(0, "test");
                            
                                private final int id;
                                private final String name;
                                EnumWithId(int id, String name) {
                                    this.id = id;
                                    this.name = name;
                                }
                                public int getId() {
                                    return id;
                                }
                            }
                            """)
                    .addClass("enumtestmod.TestMod", """
                            package enumtestmod;
                            @net.neoforged.fml.common.Mod("enumtestmod")
                            public class TestMod {
                                private static final java.util.List<Object> ENUM_PARAMS = java.util.List.of("enumtestmod:lazy_test_thing");
                                private static final java.util.List<Object> ID_ENUM_PARAMS = java.util.List.of(-1, "enumtestmod:lazy_id_test_thing");
                            
                                public TestMod() {
                                    System.out.println(java.util.Arrays.toString(ExtensibleEnum.values()));
                                    System.out.println(java.util.Arrays.toString(EnumWithId.values()));
                            
                                    /*
                                    ExtensibleEnum otherTestThing = ExtensibleEnum.valueOf("ENUMTESTMOD_OTHER_TEST_THING");
                                    ExtensibleEnum prefixedTestThing = ExtensibleEnum.valueOf("ENUMTESTMOD_PREFIXED_TEST_THING");
                                    ExtensibleEnum lazyTestThing = ExtensibleEnum.valueOf("ENUMTESTMOD_LAZY_TEST_THING");
                            
                                    com.google.common.base.Preconditions.checkState(otherTestThing.ordinal() == 2);
                                    com.google.common.base.Preconditions.checkState(prefixedTestThing.ordinal() == 3);
                                    com.google.common.base.Preconditions.checkState(lazyTestThing.ordinal() == 1);
                            
                                    EnumWithId otherIdTestThing = EnumWithId.valueOf("ENUMTESTMOD_OTHER_ID_TEST_THING");
                                    EnumWithId prefixedIdTestThing = EnumWithId.valueOf("ENUMTESTMOD_PREFIXED_ID_TEST_THING");
                                    EnumWithId lazyIdTestThing = EnumWithId.valueOf("ENUMTESTMOD_LAZY_ID_TEST_THING");
                            
                                    com.google.common.base.Preconditions.checkState(otherIdTestThing.ordinal() == 2);
                                    com.google.common.base.Preconditions.checkState(otherIdTestThing.getId() == 2);
                                    com.google.common.base.Preconditions.checkState(prefixedIdTestThing.ordinal() == 3);
                                    com.google.common.base.Preconditions.checkState(prefixedIdTestThing.getId() == 3);
                                    com.google.common.base.Preconditions.checkState(lazyIdTestThing.ordinal() == 1);
                                    com.google.common.base.Preconditions.checkState(lazyIdTestThing.getId() == 1);
                                    */
                                }
                            }
                            """)
                    .compile();
        }

        var result = launch("forgeclient");
        Assertions.assertDoesNotThrow(() -> loadMods(result));
    }
}
