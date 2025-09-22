/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cpw.mods.modlauncher.api.CoremodTransformationContext;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import java.util.Set;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

/**
 * Tests that Java Core Mods are correctly discovered.
 */
public class CoreModTest extends LauncherTest {
    private static final ContainedJarIdentifier JAR_IDENTIFIER = new ContainedJarIdentifier("testmod", "coremod");

    // A transformer that just adds a @Deprecated annotation, which is easy to assert for
    public static final ITransformer<ClassNode> TEST_TRANSFORMER = new ITransformer<>() {
        @Override
        public ProcessorName name() {
            return new ProcessorName("fml", "test");
        }

        @Override
        public void transform(ClassNode classNode, CoremodTransformationContext context) {
            classNode.visitAnnotation("Ljava/lang/Deprecated;", true);
        }

        @Override
        public Set<Target<ClassNode>> targets() {
            return Set.of(Target.targetClass("testmod.TestClass"));
        }

        @Override
        public TargetType getTargetType() {
            return TargetType.CLASS;
        }
    };

    @Test
    public void testBrokenJijJavaCoremod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .withJarInJar(JAR_IDENTIFIER, coreMod -> {
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY.name())
                            .addService(ICoreMod.class.getName(), "testmod.coremods.TestCoreMod")
                            .addClass("testmod.coremods.TestCoreMod", """
                                    import cpw.mods.modlauncher.api.ITransformer;
                                    public class TestCoreMod implements net.neoforged.neoforgespi.coremod.ICoreMod {
                                    @Override public Iterable<? extends ITransformer<?>> getTransformers() {
                                        return null;
                                    }}""");
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
                .addService(ICoreMod.class.getName(), "testmod.coremods.TestCoreMod")
                .addClass("testmod.coremods.TestCoreMod", """
                        import cpw.mods.modlauncher.api.ITransformer;
                        public class TestCoreMod implements net.neoforged.neoforgespi.coremod.ICoreMod {
                        @Override public Iterable<? extends ITransformer<?>> getTransformers() {
                            return null;
                        }}""")
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
                            .addService(ICoreMod.class.getName(), "testmod.coremods.TestCoreMod")
                            .addClass("testmod.coremods.TestCoreMod", """
                                    import cpw.mods.modlauncher.api.ITransformer;
                                    import java.util.List;
                                    public class TestCoreMod implements net.neoforged.neoforgespi.coremod.ICoreMod {
                                    @Override public Iterable<? extends ITransformer<?>> getTransformers() {
                                        return List.of(net.neoforged.fml.loading.CoreModTest.TEST_TRANSFORMER);
                                    }}""");
                })
                .build();

        var result = launchAndLoad("neoforgeclient");

        var testClass = Class.forName("testmod.TestClass", true, result.launchClassLoader());
        assertThat(testClass).hasAnnotation(Deprecated.class); // This is added by the transformer

        assertEquals("neoforge:computing_frames,fml:test", loader.getClassTransformerAuditLog().getAuditString("testmod.TestClass"));
    }
}
