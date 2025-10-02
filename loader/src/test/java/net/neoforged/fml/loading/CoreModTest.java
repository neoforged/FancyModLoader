/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

/**
 * Tests that Java Core Mods are correctly discovered.
 */
public class CoreModTest extends LauncherTest {
    private static final ContainedJarIdentifier JAR_IDENTIFIER = new ContainedJarIdentifier("testmod", "coremod");

    // A transformer that just adds a @Deprecated annotation, which is easy to assert for
    public static final ClassProcessor TEST_TRANSFORMER = new SimpleClassProcessor() {
        @Override
        public ProcessorName name() {
            return new ProcessorName("fml", "test");
        }

        @Override
        public void transform(ClassNode classNode, SimpleTransformationContext context) {
            classNode.visitAnnotation("Ljava/lang/Deprecated;", true);
        }

        @Override
        public Set<Target> targets() {
            return Set.of(new Target("testmod.TestClass"));
        }
    };

    @Test
    public void testBrokenJijJavaCoremod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .withJarInJar(JAR_IDENTIFIER, coreMod -> {
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY.name())
                            .addService(ClassProcessorProvider.class.getName(), "testmod.coremods.TestCoreMod")
                            .addClass("testmod.coremods.TestCoreMod", """
                                    public class TestCoreMod implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                                        @Override
                                        public void makeProcessors(Context context, Collector collector) {
                                            throw new RuntimeException();
                                        }
                                    }""");
                })
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: An error occurred while loading core-mod testmod.coremods.TestCoreMod from mods/testmod.jar > coremod-1.0.jar");
    }

    @Test
    public void testBrokenJavaCoreMod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("coremod.jar")
                .withModTypeManifest(IModFile.Type.LIBRARY.name())
                .addService(ClassProcessorProvider.class.getName(), "testmod.coremods.TestCoreMod")
                .addClass("testmod.coremods.TestCoreMod", """
                        public class TestCoreMod implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                            @Override
                            public void makeProcessors(Context context, Collector collector) {
                                throw new RuntimeException();
                            }
                        }""")
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: An error occurred while loading core-mod testmod.coremods.TestCoreMod from mods/coremod.jar");
    }

    @Test
    public void testJavaCoreMod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .addClass("testmod.TestClass", """
                        class TestClass {}
                        """)
                .withJarInJar(JAR_IDENTIFIER, coreMod -> {
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY.name())
                            .addService(ClassProcessorProvider.class.getName(), "testmod.coremods.TestCoreMod")
                            .addClass("testmod.coremods.TestCoreMod", """
                                    public class TestCoreMod implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                                        @Override
                                        public void makeProcessors(Context context, Collector collector) {
                                            collector.add(net.neoforged.fml.loading.CoreModTest.TEST_TRANSFORMER);
                                        }
                                    }""");
                })
                .build();

        var result = launchAndLoad("neoforgeclient");

        var testClass = Class.forName("testmod.TestClass", true, result.launchClassLoader());
        assertThat(testClass).hasAnnotation(Deprecated.class); // This is added by the transformer

        assertEquals("fml:test", loader.getClassTransformerAuditLog().getAuditString("testmod.TestClass"));
    }
}
