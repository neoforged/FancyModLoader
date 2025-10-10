/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Set;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.testlib.ModFileBuilder;
import net.neoforged.fml.testlib.SimulatedInstallation;
import net.neoforged.fml.testlib.args.ClientInstallationTypesSource;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.objectweb.asm.tree.ClassNode;

/**
 * Tests that simple class processors (the replacement to Java Core Mods) are correctly discovered.
 */
public class SimpleProcessorsTest extends LauncherTest {
    private static final ContainedJarIdentifier JAR_IDENTIFIER = new ContainedJarIdentifier("testmod", "simpleprocessors");

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
    public void testBrokenJijSimpleProcessor() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .withJarInJar(JAR_IDENTIFIER, coreMod -> {
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY.name())
                            .addService(ClassProcessorProvider.class.getName(), "testmod.simpleprocessors.TestSimpleProcessors")
                            .addClass("testmod.simpleprocessors.TestSimpleProcessors", """
                                    public class TestSimpleProcessors implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                                        @Override
                                        public void createProcessors(Context context, Collector collector) {
                                            throw new RuntimeException();
                                        }
                                    }""");
                })
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: An error occurred while loading class processor testmod.simpleprocessors.TestSimpleProcessors from mods/testmod.jar > simpleprocessors-1.0.jar");
    }

    @Test
    public void testBrokenSimpleProcessor() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("simpleprocessors.jar")
                .withModTypeManifest(IModFile.Type.LIBRARY.name())
                .addService(ClassProcessorProvider.class.getName(), "testmod.simpleprocessors.TestSimpleProcessors")
                .addClass("testmod.simpleprocessors.TestSimpleProcessors", """
                        public class TestSimpleProcessors implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                            @Override
                            public void createProcessors(Context context, Collector collector) {
                                throw new RuntimeException();
                            }
                        }""")
                .build();

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: An error occurred while loading class processor testmod.simpleprocessors.TestSimpleProcessors from mods/simpleprocessors.jar");
    }

    @Test
    public void testSimpleProcessor() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .addClass("testmod.TestClass", """
                        class TestClass {}
                        """)
                .withJarInJar(JAR_IDENTIFIER, coreMod -> {
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY.name())
                            .addService(ClassProcessorProvider.class.getName(), "testmod.simpleprocessors.TestSimpleProcessors")
                            .addClass("testmod.simpleprocessors.TestSimpleProcessors", """
                                    public class TestSimpleProcessors implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                                        @Override
                                        public void createProcessors(Context context, Collector collector) {
                                            collector.add(net.neoforged.fml.loading.SimpleProcessorsTest.TEST_TRANSFORMER);
                                        }
                                    }""");
                })
                .build();

        var result = launchAndLoad("neoforgeclient");

        var testClass = Class.forName("testmod.TestClass", true, result.launchClassLoader());
        assertThat(testClass).hasAnnotation(Deprecated.class); // This is added by the transformer

        assertEquals("fml:test", loader.getClassTransformerAuditLog().getAuditString("testmod.TestClass"));
    }

    /**
     * Class processors shouldn't be able to be loaded from mod jars, and the modder should get a proper error
     * for this case.
     */
    @ParameterizedTest
    @ClientInstallationTypesSource
    public void testProcessorProvidedByModJar(SimulatedInstallation.Type type) throws Exception {
        testProcessorProvidedByGameContent(type, ModFileBuilder::withTestmodModsToml);
    }

    /**
     * Class processors shouldn't be able to be loaded from game libraries, and the modder should get a proper error
     * for this case.
     */
    @ParameterizedTest
    @ClientInstallationTypesSource
    public void testProcessorProvidedByGameLibrary(SimulatedInstallation.Type type) throws Exception {
        testProcessorProvidedByGameContent(type, builder -> builder.withModTypeManifest("GAMELIBRARY"));
    }

    private void testProcessorProvidedByGameContent(SimulatedInstallation.Type type, ModFileBuilder.ModJarCustomizer customizer) throws IOException {
        installation.setup(type);
        installation.buildInstallationAppropriateModProject("testmod", "testmod.jar", builder -> {
            customizer.customize(builder);
            builder.addClass("testmod.TestProvider", """
                    public class TestProvider implements net.neoforged.neoforgespi.transformation.ClassProcessorProvider {
                        @Override
                        public void createProcessors(Context context, Collector collector) {
                            throw new RuntimeException();
                        }
                    }""")
                    .addService(ClassProcessorProvider.class, "testmod.TestProvider");
        });

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        var issues = getTranslatedIssues(e.getIssues());
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst()).matches(
                "ERROR: Mod file .* is trying to provide a class processor. Mods and game libraries cannot provide such services, only libraries can.");
    }
}
