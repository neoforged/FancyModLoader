/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
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

    public static final ITransformer<?> TEST_TRANSFORMER = mock(ITransformer.class);

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

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
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

        var e = assertThrows(ModLoadingException.class, () -> launchAndLoad("forgeclient"));
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly(
                "ERROR: An error occurred while loading core-mod testmod.coremods.TestCoreMod from mods/coremod.jar");
    }

    @Test
    public void testJavaCoreMod() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("testmod.jar")
                .withTestmodModsToml()
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

        var transformers = launchAndLoad("forgeclient").transformers();
        assertThat(transformers).containsOnly(TEST_TRANSFORMER);
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
                .addTextFile("coremods/test.js", """
                        function initializeCoreMod() {
                            return {
                                'test': {
                                    'target': {
                                        'type': 'CLASS',
                                        'name': 'net.minecraft.world.level.biome.Biome'
                                    },
                                    'transformer': function(classNode) {
                                        return classNode;
                                    }
                                }
                            }
                        }
                        """)
                .build();

        var transformers = launchAndLoad("forgeclient").transformers();
        assertThat(transformers).hasSize(1);
        var transformer = (ITransformer<ClassNode>) transformers.getFirst();
        assertThat(transformer.getTargetType()).isEqualTo(TargetType.CLASS);
        assertThat(transformer.targets()).containsOnly(
                ITransformer.Target.targetClass("net.minecraft.world.level.biome.Biome"));
    }
}
