/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.Set;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.locating.IModFile;
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
        public ClassNode transform(ClassNode classNode, ITransformerVotingContext context) {
            classNode.visitAnnotation("Ljava/lang/Deprecated;", true);
            return classNode;
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public Set<Target<ClassNode>> targets() {
            return Set.of(Target.targetClass("testmod.TestClass"));
        }

        @Override
        public TargetType<ClassNode> getTargetType() {
            return TargetType.CLASS;
        }
    };

    @Test
    public void testBrokenJijJavaCoremod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .withJarInJar(JAR_IDENTIFIER, coreMod -> {
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY)
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
                .withModTypeManifest(IModFile.Type.LIBRARY)
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
                    coreMod.withModTypeManifest(IModFile.Type.LIBRARY)
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

        var transformers = launchAndLoad("neoforgeclient").transformers();
        assertThat(transformers).containsOnly(TEST_TRANSFORMER);

        var testClass = Class.forName("testmod.TestClass", true, gameClassLoader);
        assertThat(testClass).hasAnnotation(Deprecated.class); // This is added by the transformer
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testJavaScriptCoremod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
                .addTextFile("META-INF/coremods.json", """
                        {
                            "coremodid": "coremods/test.js"
                        }
                        """)
                .addClass("net.minecraft.world.level.biome.Biome", "class Biome {}")
                .addTextFile("coremods/test.js", """
                        function initializeCoreMod() {
                            return {
                                'test': {
                                    'target': {
                                        'type': 'CLASS',
                                        'name': 'net.minecraft.world.level.biome.Biome'
                                    },
                                    'transformer': function(classNode) {
                                        classNode.visitAnnotation("Ljava/lang/Deprecated;", true);
                                        return classNode;
                                    }
                                }
                            }
                        }
                        """)
                .build();

        var transformers = launchAndLoad("neoforgeclient").transformers();
        assertThat(transformers).hasSize(1);
        var transformer = (ITransformer<ClassNode>) transformers.getFirst();
        assertThat(transformer.getTargetType()).isEqualTo(TargetType.CLASS);
        assertThat(transformer.targets()).containsOnly(
                ITransformer.Target.targetClass("net.minecraft.world.level.biome.Biome"));

        var testClass = Class.forName("net.minecraft.world.level.biome.Biome", true, gameClassLoader);
        assertThat(testClass).hasAnnotation(Deprecated.class); // This is added by the transformer
    }
}
