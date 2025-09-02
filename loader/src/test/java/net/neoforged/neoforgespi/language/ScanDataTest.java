/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.fml.test.TestModFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

public class ScanDataTest {
    @Test
    void testCantGetScanResultsWithoutStartingScan() throws IOException {
        try (final var mod = modFile()) {
            var e = assertThrows(IllegalStateException.class, mod::getScanResult);
            assertThat(e).hasMessageContaining("Scanning of this mod file has not started yet.");
        }
    }

    @Test
    void testCannotStartScanTwice() throws IOException {
        try (final var mod = modFile()) {
            mod.scan();
            var e = assertThrows(IllegalStateException.class, mod::scan);
            assertThat(e).hasMessageContaining("The mod file scan was already started.");
        }
    }

    @Test
    void testSimpleAnnotations() throws IOException {
        try (final var mod = modFile()) {
            mod.classBuilder()
                    .addClass("com.example.sc.SimpleCustom", """
                            @interface SimpleCustom {
                                String value() default "";
                            }

                            @SimpleCustom()
                            class C1 {

                            }

                            @SimpleCustom("some string")
                            class C2 {
                            }""")
                    .compile();
            mod.scan();

            final var type = Type.getObjectType("com/example/sc/SimpleCustom");
            assertThat(mod.getScanResult().getAnnotations())
                    .filteredOn(ad -> ad.annotationType().equals(type))
                    .filteredOn(ad -> ad.targetType() == ElementType.TYPE)
                    .containsOnly(
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.TYPE, Type.getObjectType("com/example/sc/C1"), "com.example.sc.C1", Map.of()),
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.TYPE, Type.getObjectType("com/example/sc/C2"), "com.example.sc.C2", Map.of("value", "some string")));
        }
    }

    @Test
    void testAnnotationsWithLists() throws IOException {
        try (final var mod = modFile()) {
            mod.classBuilder()
                    .addClass("com.example.lst.WithList", """
                            @interface WithList {
                                String[] value();
                            }

                            @WithList({"str1", "str2"})
                            class C1 {
                            }
                            """)
                    .compile();
            mod.scan();

            final var type = Type.getObjectType("com/example/lst/WithList");
            assertThat(mod.getScanResult().getAnnotations())
                    .filteredOn(ad -> ad.annotationType().equals(type))
                    .filteredOn(ad -> ad.targetType() == ElementType.TYPE)
                    .containsOnly(
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.TYPE, Type.getObjectType("com/example/lst/C1"), "com.example.lst.C1", Map.of(
                                            "value", List.of("str1", "str2"))));
        }
    }

    @Test
    void testAnnotationsWithEnumLists() throws IOException {
        try (final var mod = modFile()) {
            mod.classBuilder()
                    .addClass("com.example.WithEnumList", """
                            enum SomeEnum {
                                VAL1,
                                VAL2,
                                VAL3
                            }

                            @interface WithEnumList {
                                SomeEnum[] value();
                            }

                            @WithEnumList({SomeEnum.VAL1, SomeEnum.VAL3})
                            class C1 {
                            }""")
                    .compile();
            mod.scan();

            final var type = Type.getObjectType("com/example/WithEnumList");
            assertThat(mod.getScanResult().getAnnotations())
                    .filteredOn(ad -> ad.annotationType().equals(type))
                    .filteredOn(ad -> ad.targetType() == ElementType.TYPE)
                    .containsOnly(
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.TYPE, Type.getObjectType("com/example/C1"), "com.example.C1", Map.of(
                                            "value", List.of(
                                                    new ModAnnotation.EnumHolder("Lcom/example/SomeEnum;", "VAL1"),
                                                    new ModAnnotation.EnumHolder("Lcom/example/SomeEnum;", "VAL3")))));
        }
    }

    @Test
    void testMethodAnnotations() throws IOException {
        try (final var mod = modFile()) {
            mod.classBuilder()
                    .addClass("com.example.MethodTest", """
                            @interface SomeAnn {
                            }

                            class MethodTest {
                                @SomeAnn
                                public void run() {
                                }
                            }""")
                    .compile();

            mod.scan();

            final var type = Type.getObjectType("com/example/SomeAnn");
            assertThat(mod.getScanResult().getAnnotations())
                    .filteredOn(ad -> ad.annotationType().equals(type))
                    .filteredOn(ad -> ad.targetType() == ElementType.METHOD)
                    .containsOnly(
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.METHOD, Type.getObjectType("com/example/MethodTest"), "run()V", Map.of()));
        }
    }

    @Test
    void testFieldAnnotations() throws IOException {
        try (final var mod = modFile()) {
            mod.classBuilder()
                    .addClass("com.example.FieldTest", """
                            @interface SomeAnn {
                            }

                            class FieldTest {
                                @SomeAnn
                                public int counter;
                            }""")
                    .compile();

            mod.scan();

            final var type = Type.getObjectType("com/example/SomeAnn");
            assertThat(mod.getScanResult().getAnnotations())
                    .filteredOn(ad -> ad.annotationType().equals(type))
                    .filteredOn(ad -> ad.targetType() == ElementType.FIELD)
                    .containsOnly(
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.FIELD, Type.getObjectType("com/example/FieldTest"), "counter", Map.of()));
        }
    }

    private static TestModFile modFile() throws IOException {
        return TestModFile.newInstance("""
                license="LGPL v3"

                [[mods]]
                modId="testmod"
                version="1.0"
                """);
    }
}
