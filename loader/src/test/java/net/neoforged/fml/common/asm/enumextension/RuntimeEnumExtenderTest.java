/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Consumer;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.LauncherTest;
import net.neoforged.fml.loading.ModsTomlBuilder;
import org.junit.jupiter.api.Test;

class RuntimeEnumExtenderTest extends LauncherTest {
    @Test
    void testMissingPath() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("testmod.jar")
                .withModsToml(getModsTomlBuilderConsumer("xyz"))
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: Enum extender file xyz, provided by mod testmod, does not exist");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void testExtendEnum() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("testmod.jar")
                .withModsToml(getModsTomlBuilderConsumer("extensions.json"))
                .addTextFile("extensions.json", """
                        {
                            "entries": [
                                {
                                    "enum": "testmod/SomeEnum",
                                    "name": "TESTMOD_NEW_CONSTANT",
                                    "constructor": "()V",
                                    "parameters": []
                                }
                            ]
                        }
                        """)
                .addClass("testmod.SomeEnum", """
                        import net.neoforged.fml.common.asm.enumextension.IExtensibleEnum;
                        import net.neoforged.fml.common.asm.enumextension.ExtensionInfo;
                        enum SomeEnum implements IExtensibleEnum {
                            LITERAL;
                            public static ExtensionInfo getExtensionInfo() {
                                return ExtensionInfo.nonExtended(SomeEnum.class);
                            }
                        }
                        """)
                .build();

        launchAndLoad("forgeclient");

        var enumClass = (Class<? extends Enum>) Class.forName("testmod.SomeEnum", true, gameClassLoader);

        assertThat(enumClass).hasSuperclass(Enum.class);
        assertThat(enumClass.getEnumConstants()).extracting(Enum::name).containsExactly(
                "LITERAL", "TESTMOD_NEW_CONSTANT");
        assertThat(enumClass.getEnumConstants()).extracting(Enum::ordinal).containsExactly(
                0, 1);
        assertThat(Enum.valueOf(enumClass, "LITERAL")).isInstanceOf(enumClass);
        assertThat(Enum.valueOf(enumClass, "TESTMOD_NEW_CONSTANT")).isInstanceOf(enumClass);
    }

    @Test
    public void testEnumExtension() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("enum_ext_test.jar")
                .withModsToml(builder -> builder
                        .unlicensedJavaMod()
                        .addMod("enumtestmod", "1.0", config -> config.set("enumExtensions", "META-INF/enumextensions.json")))
                .addTextFile("META-INF/enumextensions.json", """
                        {
                            "entries": [
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
                                    "enum": "enumtestmod/ExtensibleEnum",
                                    "name": "ENUMTESTMOD_MTH_LAZY_TEST_THING",
                                    "constructor": "(Ljava/lang/String;)V",
                                    "parameters": {
                                        "class": "enumtestmod/TestMod",
                                        "method": "getEnumParameter"
                                    }
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
                                },
                                {
                                    "enum": "enumtestmod/EnumWithId",
                                    "name": "ENUMTESTMOD_MTH_LAZY_ID_TEST_THING",
                                    "constructor": "(ILjava/lang/String;)V",
                                    "parameters": {
                                        "class": "enumtestmod/TestMod",
                                        "method": "getIdEnumParameter"
                                    }
                                }
                            ]
                        }
                        """)
                .addClass("enumtestmod.ExtensibleEnum", """
                        package enumtestmod;
                        @net.neoforged.fml.common.asm.enumextension.NamedEnum
                        public enum ExtensibleEnum implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
                            TEST_THING("test");
                            private final String name;
                            ExtensibleEnum(String name) {
                                this.name = name;
                            }
                            public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
                                return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(ExtensibleEnum.class);
                            }
                        }
                        """)
                .addClass("enumtestmod.EnumWithId", """
                        package enumtestmod;
                        @net.neoforged.fml.common.asm.enumextension.IndexedEnum
                        @net.neoforged.fml.common.asm.enumextension.NamedEnum(1)
                        public enum EnumWithId implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
                            TEST_ID_THING(0, "test");
                            private final int id;
                            private final String name;
                            EnumWithId(int id, String name) {
                                this.id = id;
                                this.name = name;
                            }
                            public int getId() {
                                return id;
                            }
                            public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
                                return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(EnumWithId.class);
                            }
                        }
                        """)
                .addClass("enumtestmod.TestMod", """
                        package enumtestmod;
                        @net.neoforged.fml.common.Mod("enumtestmod")
                        public class TestMod {
                            public static final net.neoforged.fml.common.asm.enumextension.EnumProxy<ExtensibleEnum> ENUM_PARAMS =
                                    new net.neoforged.fml.common.asm.enumextension.EnumProxy<>(ExtensibleEnum.class, "enumtestmod:lazy_test_thing");
                            public static final net.neoforged.fml.common.asm.enumextension.EnumProxy<EnumWithId> ID_ENUM_PARAMS =
                                    new net.neoforged.fml.common.asm.enumextension.EnumProxy<>(EnumWithId.class, -1, "enumtestmod:lazy_id_test_thing");
                            public TestMod() {
                                System.out.println(java.util.Arrays.toString(ExtensibleEnum.values()));
                                System.out.println(java.util.Arrays.toString(EnumWithId.values()));
                                System.out.println(ENUM_PARAMS.getValue());
                                System.out.println(ID_ENUM_PARAMS.getValue());
                                ExtensibleEnum prefixedTestThing = ExtensibleEnum.valueOf("ENUMTESTMOD_PREFIXED_TEST_THING");
                                ExtensibleEnum lazyTestThing = ExtensibleEnum.valueOf("ENUMTESTMOD_LAZY_TEST_THING");
                                ExtensibleEnum mthLazyTestThing = ExtensibleEnum.valueOf("ENUMTESTMOD_MTH_LAZY_TEST_THING");
                                com.google.common.base.Preconditions.checkState(prefixedTestThing.ordinal() == 3);
                                com.google.common.base.Preconditions.checkState(lazyTestThing.ordinal() == 1);
                                com.google.common.base.Preconditions.checkState(mthLazyTestThing.ordinal() == 2);
                                EnumWithId prefixedIdTestThing = EnumWithId.valueOf("ENUMTESTMOD_PREFIXED_ID_TEST_THING");
                                EnumWithId lazyIdTestThing = EnumWithId.valueOf("ENUMTESTMOD_LAZY_ID_TEST_THING");
                                EnumWithId mthLazyIdTestThing = EnumWithId.valueOf("ENUMTESTMOD_MTH_LAZY_ID_TEST_THING");
                                com.google.common.base.Preconditions.checkState(prefixedIdTestThing.ordinal() == 3);
                                com.google.common.base.Preconditions.checkState(prefixedIdTestThing.getId() == 3);
                                com.google.common.base.Preconditions.checkState(lazyIdTestThing.ordinal() == 1);
                                com.google.common.base.Preconditions.checkState(lazyIdTestThing.getId() == 1);
                                com.google.common.base.Preconditions.checkState(mthLazyIdTestThing.ordinal() == 2);
                                com.google.common.base.Preconditions.checkState(mthLazyIdTestThing.getId() == 2);
                                System.out.println(ExtensibleEnum.getExtensionInfo());
                                System.out.println(EnumWithId.getExtensionInfo());
                            }
                            public static Object getEnumParameter(int idx, Class<?> type) {
                                if (idx == 0) {
                                    return type.cast("enumtestmod:mth_lazy_test_thing");
                                }
                                throw new IllegalArgumentException("Unexpected param idx: " + idx);
                            }
                            public static Object getIdEnumParameter(int idx, Class<?> type) {
                                return type.cast(switch (idx) {
                                    case 0 -> -1;
                                    case 1 -> "enumtestmod:mth_lazy_id_test_thing";
                                    default -> throw new IllegalArgumentException("Unexpected param idx: " + idx);
                                });
                            }
                        }
                        """)
                .build();
        launchAndLoad("forgeclient");
    }

    private static Consumer<ModsTomlBuilder> getModsTomlBuilderConsumer(String extensionPath) {
        return builder -> {
            builder.unlicensedJavaMod();
            builder.addMod("testmod", "1.0", config -> {
                config.set("enumExtensions", extensionPath);
            });
        };
    }
}
