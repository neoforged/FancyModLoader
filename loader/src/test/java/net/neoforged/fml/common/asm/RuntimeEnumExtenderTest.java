/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import net.neoforged.fml.loading.SimulatedInstallation;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

class RuntimeEnumExtenderTest {
    private static final String ENUM_CLASS = "baseenum.BaseEnum";

    private final SimulatedInstallation installation;

    private URLClassLoader classLoader;

    public RuntimeEnumExtenderTest() throws Exception {
        installation = new SimulatedInstallation();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (classLoader != null) {
            classLoader.close();
        }
        installation.close();
    }

    @Test
    void testEnumWithoutFactoryMethod() {
        var e = assertThrows(Exception.class, () -> loadTransformedEnumClass(
                """
                        public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                        }
                        """));
        assertThat(e).hasMessageContaining("has no candidate factory methods: baseenum.BaseEnum");
    }

    @Test
    void testEnumFactoryMethodWithNoStringArg() {
        var e = assertThrows(Exception.class, () -> loadTransformedEnumClass(
                """
                        public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                            ;

                            public static BaseEnum create() {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """));
        assertThat(e).hasMessageContaining("Enum has create method without String as first parameter");
    }

    @Test
    void testEnumFactoryMethodWithMissingArgs() {
        var e = assertThrows(Exception.class, () -> loadTransformedEnumClass(
                """
                        public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                            ;
                            BaseEnum(int arg) {}

                            public static BaseEnum create(String name) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """));
        assertThat(e).hasMessageContaining("Enum has create method with no matching constructor");
    }

    @Test
    void testEnumFactoryMethodWithWrongArgs() {
        var e = assertThrows(Exception.class, () -> loadTransformedEnumClass(
                """
                        public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                            ;
                            BaseEnum(int arg) {}

                            public static BaseEnum create(String name, String arg) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """));
        assertThat(e).hasMessageContaining("Enum has create method with no matching constructor");
    }

    @Test
    void testEnumFactoryHasIncorrectReturnType() {
        var e = assertThrows(Exception.class, () -> loadTransformedEnumClass(
                """
                        public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                            ;

                            public static String create(String name) {
                                throw new UnsupportedOperationException();
                            }
                        }
                        """));
        assertThat(e).hasMessageContaining("Enum has create method with incorrect return type");
    }

    @Nested
    public class SimpleEnum<T extends Enum<T>> {
        Class<T> enumClass;
        T existingConstant;
        T newConstant;

        public SimpleEnum() throws Exception {
            enumClass = loadTransformedEnumClass(
                    """
                            public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                                EXISTING_CONSTANT;
                                public static BaseEnum create(String name) {
                                  throw new UnsupportedOperationException();
                                }
                            }
                            """);

            // Ensure that the caches are populated to ensure they are cleared when we create a new constant
            existingConstant = Enum.valueOf(enumClass, "EXISTING_CONSTANT");
            assertThat(existingConstant).isNotNull();
            assertThat(enumClass.getEnumConstants()).containsExactly(existingConstant);

            newConstant = createConstant(enumClass, "NEW_CONSTANT");
        }

        @Test
        void testNewConstantHasRightType() throws Exception {
            assertThat(newConstant).isInstanceOf(enumClass);
        }

        @Test
        void testName() {
            assertEquals("NEW_CONSTANT", newConstant.name());
        }

        @Test
        void testOrdinal() {
            assertEquals(1, newConstant.ordinal());
        }

        @Test
        void testGetEnumConstants() {
            assertThat(enumClass.getEnumConstants()).containsExactly(existingConstant, newConstant);
        }

        @Test
        void testValueOf() {
            assertThat(Enum.valueOf(enumClass, "NEW_CONSTANT")).isSameAs(newConstant);
        }

        @Test
        void testAddDuplicateConstant() throws Exception {
            assertSame(newConstant, createConstant(enumClass, "NEW_CONSTANT"));
        }
    }

    @Nested
    public class EnumWithArgs<T extends Enum<T>> {
        Class<T> enumClass;
        T existingConstant;
        T newConstant;
        Method createMethod;

        @SuppressWarnings("unchecked")
        public EnumWithArgs() throws Exception {
            enumClass = loadTransformedEnumClass(
                    """
                            public enum BaseEnum implements net.neoforged.neoforge.common.IExtensibleEnum {
                                EXISTING_CONSTANT(1234, "abc");
                                public final int a;
                                public final String b;
                                BaseEnum(int a, String b) {
                                    this.a = a;
                                    this.b = b;
                                }
                                public static BaseEnum create(String name, int a, String b) {
                                  throw new UnsupportedOperationException();
                                }
                                public String toString() { return name() + ";" + ordinal() + ";" + a + ";" + b; }
                            }
                            """);

            // Ensure that the caches are populated to ensure they are cleared when we create a new constant
            existingConstant = Enum.valueOf(enumClass, "EXISTING_CONSTANT");
            assertThat(existingConstant).hasToString("EXISTING_CONSTANT;0;1234;abc");
            assertThat(enumClass.getEnumConstants()).containsExactly(existingConstant);

            createMethod = enumClass.getDeclaredMethod("create", String.class, int.class, String.class);
            newConstant = (T) createMethod.invoke(null, "NEW_CONSTANT", 9999, "xxx");
        }

        @Test
        void testNewConstantHasRightType() throws Exception {
            assertThat(newConstant).isInstanceOf(enumClass);
        }

        @Test
        void testProperties() {
            // We encode the relevant properties in toString for easier testing
            assertThat(newConstant).hasToString("NEW_CONSTANT;1;9999;xxx");
        }

        @Test
        void testGetEnumConstants() {
            assertThat(enumClass.getEnumConstants()).containsExactly(existingConstant, newConstant);
        }

        @Test
        void testValueOf() {
            assertThat(Enum.valueOf(enumClass, "NEW_CONSTANT")).isSameAs(newConstant);
        }

        @Test
        void testAddDuplicateConstantWithSameArgs() throws Exception {
            assertSame(newConstant, createMethod.invoke(enumClass, "NEW_CONSTANT", 9999, "xxx"));
        }

        @Test
        void testAddDuplicateConstantWithDifferentArgs() throws Exception {
            // Yes, even when invoked with different args, the same constant is returned
            assertSame(newConstant, createMethod.invoke(enumClass, "NEW_CONSTANT", 9999, "zzz"));
        }

        @SuppressWarnings("unchecked")
        @Test
        void testAddThirdConstant() throws Exception {
            var thirdConstant = (T) createMethod.invoke(enumClass, "THIRD_CONSTANT", 9999, "zzz");
            assertNotSame(newConstant, thirdConstant);
            assertThat(thirdConstant).hasToString("THIRD_CONSTANT;2;9999;zzz");
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> Class<T> loadTransformedEnumClass(@Language("java") String enumClassContent) throws Exception {
        var modJarPath = installation.buildModJar("test.jar")
                .addClass("net.neoforged.neoforge.common.IExtensibleEnum", """
                        public interface IExtensibleEnum {
                            default void init() {}
                        }
                        """)
                .addClass(ENUM_CLASS, enumClassContent)
                .build();

        var transformer = new RuntimeEnumExtender();
        var transformedJar = modJarPath.resolveSibling("test_transformed.jar");

        // In the absence of a transforming class-loader, pre-transform everything
        try (var in = new JarInputStream(Files.newInputStream(modJarPath))) {
            try (var out = new JarOutputStream(Files.newOutputStream(transformedJar), in.getManifest())) {
                for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {

                    out.putNextEntry(entry);
                    if (entry.getName().endsWith(".class")) {
                        var classData = in.readAllBytes();
                        var classNode = new ClassNode();
                        ClassReader classReader = new ClassReader(classData);
                        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

                        // TRANSFORM CLASS
                        var enumType = Type.getType("L" + entry.getName().replaceAll("\\.class$", "") + ";");
                        transformer.processClassWithFlags(
                                ILaunchPluginService.Phase.BEFORE,
                                classNode,
                                enumType,
                                "");

                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                        classNode.accept(cw);
                        out.write(cw.toByteArray());
                    } else {
                        in.transferTo(out);
                    }
                    out.closeEntry();
                }
            }
        }

        // Then load the transformed result into a class-loader to make sure it works and is valid
        classLoader = new URLClassLoader(new URL[] { transformedJar.toUri().toURL() }, getClass().getClassLoader());
        return (Class<T>) classLoader.loadClass(ENUM_CLASS);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T createConstant(Class<T> enumClass, String name) throws Exception {
        var createMethod = enumClass.getDeclaredMethod("create", String.class);
        return (T) createMethod.invoke(null, name);
    }
}
