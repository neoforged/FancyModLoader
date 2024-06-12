/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.LauncherTest;
import net.neoforged.fml.loading.ModsTomlBuilder;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeEnumExtenderTest extends LauncherTest {

    @Test
    void testMissingPath() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("testmod.jar")
                .withModsToml(getModsTomlBuilderConsumer("xyz"))
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: Enum extender file xyz, provided by mod testmod, does not exist"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
                "LITERAL", "TESTMOD_NEW_CONSTANT"
        );
        assertThat(enumClass.getEnumConstants()).extracting(Enum::ordinal).containsExactly(
                0, 1
        );
        assertThat(Enum.valueOf(enumClass, "LITERAL")).isInstanceOf(enumClass);
        assertThat(Enum.valueOf(enumClass, "TESTMOD_NEW_CONSTANT")).isInstanceOf(enumClass);
    }

    private static Consumer<ModsTomlBuilder> getModsTomlBuilderConsumer(String extensionPath) {
        return builder -> {
            builder.unlicensedJavaMod();
            builder.addMod("testmod", "1.0", config -> {
                config.set("enumExtender", extensionPath);
            });
        };
    }
}
