/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.ElementType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.jarhandling.SecureJar;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.neoforged.fml.loading.LauncherTest;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.fml.loading.modscan.Scanner;
import net.neoforged.fml.test.TestModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Type;

public class ScanDataTest {
    @BeforeAll
    static void ensureAddOpensForUnionFs() {
        // We abuse the ByteBuddy agent that Mockito also uses to open java.lang to UnionFS
        var instrumentation = ByteBuddyAgent.install();
        instrumentation.redefineModule(
                MethodHandles.class.getModule(),
                Set.of(),
                Map.of(),
                Map.of("java.lang.invoke", Set.of(LauncherTest.class.getModule())),
                Set.of(),
                Map.of());
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
            Assertions.assertThat(mod.getScanResult().getAnnotations())
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
            Assertions.assertThat(mod.getScanResult().getAnnotations())
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
            Assertions.assertThat(mod.getScanResult().getAnnotations())
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
            Assertions.assertThat(mod.getScanResult().getAnnotations())
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
            Assertions.assertThat(mod.getScanResult().getAnnotations())
                    .filteredOn(ad -> ad.annotationType().equals(type))
                    .filteredOn(ad -> ad.targetType() == ElementType.FIELD)
                    .containsOnly(
                            new ModFileScanData.AnnotationData(
                                    type, ElementType.FIELD, Type.getObjectType("com/example/FieldTest"), "counter", Map.of()));
        }
    }

    @Test
    void testRoundTrip(@TempDir Path temp) throws IOException {
        var path = temp.resolve("metadata");

        try (var mod = modFile()) {
            mod.classBuilder()
                    .addClass("com.example.Test", """
                            @interface SomeAnn {
                            }

                            class FieldTest {
                                @SomeAnn
                                public int counter;
                            }""")
                    .addClass("com.anotherexample.Test2", """
                            @Deprecated
                            public class Test2 implements Runnable {
                                @Override public void run() {}
                            }""")
                    .compile();
            var res = mod.scan();

            try (var out = new ObjectOutputStream(Files.newOutputStream(path))) {
                res.write(out);
            }

            ModFileScanData newRes;
            try (var in = new ObjectInputStream(Files.newInputStream(path))) {
                newRes = ModFileScanData.read(in);
            }

            Assertions.assertThat(newRes).isNotNull();

            Assertions.assertThat(res.getAnnotations())
                    .isEqualTo(newRes.getAnnotations());

            Assertions.assertThat(res.getClasses())
                    .isEqualTo(newRes.getClasses());
        }
    }

    private static TestModFile modFile() {
        return TestModFile.newInstance("""
                modLoader="javafml"
                loaderVersion="[3,]"
                license="LGPL v3"

                [[mods]]
                modId="testmod"
                version="1.0"
                """);
    }
}
