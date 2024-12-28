/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: Enum extender file xyz, provided by mod testmod, does not exist");
    }

    @Test
    <T extends Enum<T>> void testExtendEnum() throws Exception {
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
                        public enum SomeEnum implements IExtensibleEnum {
                            LITERAL;
                            public static ExtensionInfo getExtensionInfo() {
                                return ExtensionInfo.nonExtended(SomeEnum.class);
                            }
                        }
                        """)
                .build();

        launchAndLoad("neoforgeclient");

        Class<T> enumClass = getEnumClass("testmod.SomeEnum");

        assertThat(enumClass).hasSuperclass(Enum.class);
        assertThat(enumClass.getEnumConstants()).extracting(Enum::name).containsExactly(
                "LITERAL", "TESTMOD_NEW_CONSTANT");
        assertThat(enumClass.getEnumConstants()).extracting(Enum::ordinal).containsExactly(
                0, 1);
        assertThat(Enum.valueOf(enumClass, "LITERAL")).isInstanceOf(enumClass);
        assertThat(Enum.valueOf(enumClass, "TESTMOD_NEW_CONSTANT")).isInstanceOf(enumClass);

        // Grab the enum info
        var extensionInfo = getExtensionInfo(enumClass);
        assertTrue(extensionInfo.extended());
        assertEquals(1, extensionInfo.vanillaCount());
        assertEquals(2, extensionInfo.totalCount());
        assertNull(extensionInfo.netCheck());
    }

    @Test
    void testEnumProxy() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("enum_ext_test.jar")
                .withModsToml(getModsTomlBuilderConsumer("extensions.json"))
                .addTextFile("extensions.json", """
                        {
                            "entries": [
                                {
                                    "enum": "testmod/NoArgEnum",
                                    "name": "TESTMOD_NEW_CONSTANT",
                                    "constructor": "()V",
                                    "parameters": {
                                        "class": "testmod/TestMod",
                                        "field": "NO_ARG"
                                    }
                                },
                                {
                                    "enum": "testmod/StringArgEnum",
                                    "name": "TESTMOD_NEW_CONSTANT",
                                    "constructor": "(Ljava/lang/String;)V",
                                    "parameters": {
                                        "class": "testmod/TestMod",
                                        "field": "WITH_ARG"
                                    }
                                }
                            ]
                        }
                        """)
                .addClass("testmod.NoArgEnum", """
                        @net.neoforged.fml.common.asm.enumextension.NamedEnum
                        public enum NoArgEnum implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
                            TEST_THING;
                            public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
                                return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(NoArgEnum.class);
                            }
                        }
                        """)
                .addClass("testmod.StringArgEnum", """
                        @net.neoforged.fml.common.asm.enumextension.NamedEnum
                        public enum StringArgEnum implements net.neoforged.fml.common.asm.enumextension.IExtensibleEnum {
                            TEST_THING("test");
                            private final String name;
                            StringArgEnum(String name) {
                                this.name = name;
                            }
                            public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
                                return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(StringArgEnum.class);
                            }
                        }
                        """)
                .addClass("testmod.TestMod", """
                        import net.neoforged.fml.common.asm.enumextension.EnumProxy;
                        @net.neoforged.fml.common.Mod("testmod")
                        public class TestMod {
                            public static final EnumProxy<NoArgEnum> NO_ARG =
                                    new EnumProxy<>(NoArgEnum.class, "testmod:lazy_added");
                            public static final EnumProxy<StringArgEnum> WITH_ARG =
                                    new EnumProxy<>(StringArgEnum.class, "testmod:lazy_added");
                        }
                        """)
                .build();
        launchAndLoad("neoforgeclient");

        var noArgEnum = getEnumClass("testmod.NoArgEnum");
        var stringArgEnum = getEnumClass("testmod.StringArgEnum");

        var testModClass = Class.forName("testmod.TestMod", true, gameClassLoader);
        var noArg = (EnumProxy<?>) testModClass.getField("NO_ARG").get(null);
        assertThat(noArg.getValue()).isInstanceOf(noArgEnum);

        var stringArg = (EnumProxy<?>) testModClass.getField("WITH_ARG").get(null);
        assertThat(stringArg.getValue()).isInstanceOf(stringArgEnum);
    }

    @Test
    void testNoArgEnumExtension() throws Exception {
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
                                    "name": "ENUMTESTMOD_MTH_LAZY_TEST_THING",
                                    "constructor": "(Ljava/lang/String;)V",
                                    "parameters": {
                                        "class": "enumtestmod/TestMod",
                                        "method": "getEnumParameter"
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
                .addClass("enumtestmod.TestMod", """
                        import net.neoforged.fml.common.asm.enumextension.EnumProxy;
                        public class TestMod {
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
        launchAndLoad("neoforgeclient");

        var extensibleEnum = getEnumClass("enumtestmod.ExtensibleEnum");
        assertThat(extensibleEnum.getEnumConstants()).extracting(Enum::name).containsExactly(
                "TEST_THING",
                "ENUMTESTMOD_MTH_LAZY_TEST_THING",
                "ENUMTESTMOD_PREFIXED_TEST_THING");
        assertThat(extensibleEnum.getEnumConstants()).extracting(Enum::ordinal).containsExactly(
                0,
                1,
                2);

        var extensionInfo = getExtensionInfo(extensibleEnum);
        assertTrue(extensionInfo.extended());
        assertEquals(1, extensionInfo.vanillaCount());
        assertEquals(3, extensionInfo.totalCount());
    }

    @Test
    void testIdArgEnumExtension() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("enum_ext_test.jar")
                .withModsToml(builder -> builder
                        .unlicensedJavaMod()
                        .addMod("enumtestmod", "1.0", config -> config.set("enumExtensions", "META-INF/enumextensions.json")))
                .addTextFile("META-INF/enumextensions.json", """
                        {
                            "entries": [
                                {
                                    "enum": "enumtestmod/EnumWithId",
                                    "name": "ENUMTESTMOD_PREFIXED_ID_TEST_THING",
                                    "constructor": "(ILjava/lang/String;)V",
                                    "parameters": [ -1, "enumtestmod:prefixed_id_test_thing" ]
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
                        import net.neoforged.fml.common.asm.enumextension.EnumProxy;
                        public class TestMod {
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
        launchAndLoad("neoforgeclient");

        var enumWithId = getEnumClass("enumtestmod.EnumWithId");
        assertThat(enumWithId.getEnumConstants()).extracting(Enum::name).containsExactly(
                "TEST_ID_THING",
                "ENUMTESTMOD_MTH_LAZY_ID_TEST_THING",
                "ENUMTESTMOD_PREFIXED_ID_TEST_THING");
        assertThat(enumWithId.getEnumConstants()).extracting(Enum::ordinal).containsExactly(
                0,
                1,
                2);
        var getIdMethod = enumWithId.getMethod("getId");
        assertThat(enumWithId.getEnumConstants()).extracting(getIdMethod::invoke).containsExactly(
                0,
                1,
                2);
    }

    @Test
    void testExtensionInfoNonExtended() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("enum_ext_test.jar")
                .withModsToml(builder -> builder.unlicensedJavaMod()
                        .addMod("testmod", "1.0"))
                .addClass("testmod.SomeEnum", """
                        import net.neoforged.fml.common.asm.enumextension.IExtensibleEnum;
                        import net.neoforged.fml.common.asm.enumextension.ExtensionInfo;
                        import net.neoforged.fml.common.asm.enumextension.NetworkedEnum;
                        @NetworkedEnum(NetworkedEnum.NetworkCheck.CLIENTBOUND)
                        public enum SomeEnum implements IExtensibleEnum {
                            LITERAL;
                            public static ExtensionInfo getExtensionInfo() {
                                return ExtensionInfo.nonExtended(SomeEnum.class);
                            }
                        }
                        """)
                .build();
        launchAndLoad("neoforgeclient");

        var extensibleEnum = getEnumClass("testmod.SomeEnum");
        var extensionInfo = getExtensionInfo(extensibleEnum);
        assertFalse(extensionInfo.extended());
        assertEquals(0, extensionInfo.vanillaCount());
        assertEquals(0, extensionInfo.totalCount());
        assertEquals(NetworkedEnum.NetworkCheck.CLIENTBOUND, extensionInfo.netCheck());
    }

    @Test
    void testExtensionInfoExtended() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("enum_ext_test.jar")
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
                        import net.neoforged.fml.common.asm.enumextension.NetworkedEnum;
                        @NetworkedEnum(NetworkedEnum.NetworkCheck.CLIENTBOUND)
                        public enum SomeEnum implements IExtensibleEnum {
                            LITERAL;
                            public static ExtensionInfo getExtensionInfo() {
                                return ExtensionInfo.nonExtended(SomeEnum.class);
                            }
                        }
                        """)
                .build();
        launchAndLoad("neoforgeclient");

        var extensibleEnum = getEnumClass("testmod.SomeEnum");
        var extensionInfo = getExtensionInfo(extensibleEnum);
        assertTrue(extensionInfo.extended());
        assertEquals(1, extensionInfo.vanillaCount());
        assertEquals(2, extensionInfo.totalCount());
        assertEquals(NetworkedEnum.NetworkCheck.CLIENTBOUND, extensionInfo.netCheck());
    }

    private ExtensionInfo getExtensionInfo(Class<?> enumClass) throws Exception {
        var getExtensionInfo = enumClass.getMethod("getExtensionInfo");
        return (ExtensionInfo) getExtensionInfo.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> Class<T> getEnumClass(String name) throws ClassNotFoundException {
        return (Class<T>) Class.forName(name, true, gameClassLoader);
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
