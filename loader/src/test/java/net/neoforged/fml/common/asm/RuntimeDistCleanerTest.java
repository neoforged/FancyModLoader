/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.LauncherTest;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

class RuntimeDistCleanerTest extends LauncherTest {
    @Test
    void testStripInterface() throws Exception {
        transformTestClass(Dist.CLIENT, """
                @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT, _interface = java.lang.AutoCloseable.class)
                public class Test implements AutoCloseable {
                    public void close() {}
                }
                """, clazz -> assertThat(clazz.getInterfaces()).contains(AutoCloseable.class));
    }

    @Test
    void testKeepInterface() throws Exception {
        transformTestClass(Dist.DEDICATED_SERVER, """
                @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT, _interface = java.lang.AutoCloseable.class)
                public class Test implements AutoCloseable {
                    public void close() {}
                }
                """, clazz -> assertThat(clazz.getInterfaces()).doesNotContain(AutoCloseable.class));
    }

    @Test
    void testRejectLoadingClientClassOnDedicatedServer() throws Exception {
        assertThatThrownBy(() -> transformTestClass(Dist.DEDICATED_SERVER, """
                @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT)
                public class Test {
                }
                """)).hasMessageContaining("Attempted to load class test/Test for invalid dist DEDICATED_SERVER");
    }

    @Test
    void testRejectLoadingDedicatedServerClassOnClient() throws Exception {
        assertThatThrownBy(() -> transformTestClass(Dist.CLIENT, """
                @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
                public class Test {
                }
                """)).hasMessageContaining("Attempted to load class test/Test for invalid dist CLIENT");
    }

    @Test
    void testAllowLoadingClientClassOnClient() throws Exception {
        transformTestClass(Dist.CLIENT, """
                @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT)
                public class Test {
                }
                """);
    }

    @Test
    void testAllowLoadingDedicatedServerClassOnDedicatedServer() throws Exception {
        transformTestClass(Dist.DEDICATED_SERVER, """
                @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
                public class Test {
                }
                """);
    }

    @Test
    void testRemoveField() throws Exception {
        transformTestClass(Dist.DEDICATED_SERVER, """
                public class Test {
                    @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT)
                    public int field;
                }
                """, clazz -> assertThat(clazz.getFields()).extracting(Field::getName).doesNotContain("field"));
    }

    @Test
    void testRemoveMethod() throws Exception {
        transformTestClass(Dist.DEDICATED_SERVER, """
                public class Test {
                    @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT)
                    public void method() {}
                }
                """, clazz -> assertThat(clazz.getDeclaredMethods())
                // Coverage on Gradle with jacoco inserts methods
                .filteredOn(m -> !m.getName().contains("jacoco"))
                .isEmpty());
    }

    @Test
    void testRemoveLambdasInsideOfMethod() throws Exception {
        transformTestClass(Dist.DEDICATED_SERVER, """
                import java.util.function.IntSupplier;
                public class Test {
                    @net.neoforged.api.distmarker.OnlyIn(value = net.neoforged.api.distmarker.Dist.CLIENT)
                    public IntSupplier method(int arg) {
                        return () -> arg + 1;
                    }
                }
                """, clazz -> assertThat(clazz.getDeclaredMethods())
                // Coverage on Gradle with jacoco inserts methods
                .filteredOn(m -> !m.getName().contains("jacoco"))
                .isEmpty());
    }

    private void transformTestClass(Dist dist, @Language("java") String classContent) throws Exception {
        transformTestClass(dist, classContent, ignored -> {});
    }

    private void transformTestClass(Dist dist, @Language("java") String classContent, Consumer<Class<?>> asserter) throws Exception {
        if (dist.isClient()) {
            installation.setupProductionClient();
        } else {
            installation.setupProductionServer();
        }

        installation.buildModJar("modjar.jar")
                .withTestmodModsToml()
                .addClass("test.Test", classContent)
                .build();

        launchAndLoad(dist.isClient() ? "neoforgeclient" : "neoforgeserver");

        var testClass = Class.forName("test.Test", true, gameClassLoader);
        asserter.accept(testClass);
    }
}
