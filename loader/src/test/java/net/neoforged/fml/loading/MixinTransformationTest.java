/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MixinTransformationTest extends LauncherTest implements MixinTestHelper {
    @Test
    void testMixinConsistentClassInfoWithAT() throws Exception {
        // ATs must run before mixin's ClassInfo context is captured and before mixin itself runs
        // If they run between the two, local capture will fail on ATed methods due to the inconsistency.
        
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml ->
                        modsToml.addMixinConfig("test.mixins.json").addAccessTransformer("accesstransformer.cfg").addMod("test"))
                .addTextFile("test.mixins.json", """
                        {
                            "package": "test.mixin",
                            "mixins": ["NeedsAccessTransformVisible"]
                        }
                        """)
                .addTextFile("accesstransformer.cfg", """
                        public test.target.Target foo()V
                        """)
                .addClass("test.target.Target", """
                        import net.neoforged.fml.common.Mod;

                        @Mod("test")
                        public class Target {
                            {
                                foo();
                            }

                            private static void foo() {
                                int a = "abcd".length();
                            }
                        }
                        """)
                .addClass("test.target.IFace", """
                        public interface IFace {}
                        """)
                .addClass("test.mixin.NeedsAccessTransformVisible", """
                        import org.spongepowered.asm.mixin.Mixin;
                        import org.spongepowered.asm.mixin.injection.At;
                        import org.spongepowered.asm.mixin.injection.Inject;
                        import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
                        import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
                        import test.target.Target;

                        @Mixin(Target.class)
                        public class NeedsAccessTransformVisible implements test.target.IFace {
                            @Inject(
                                method = "foo",
                                at = @At("RETURN"),
                                locals = LocalCapture.CAPTURE_FAILHARD
                            )
                            private static void testInject(CallbackInfo ci, int a) {}
                        }
                        """)
                .build();

        var result = launchAndLoad("neoforgeclient");
        var testClass = result.launchClassLoader().loadClass("test.target.Target");
        var injectedInterface = result.launchClassLoader().loadClass("test.target.IFace");
        assertTrue(injectedInterface.isAssignableFrom(testClass));
    }
    
    @Test
    void testFrameRecomputationWithGeneratedClasses() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("mixin-test.jar")
                .withTestmodModsToml(modsToml -> modsToml.addMixinConfig("test.mixins.json").addMod("test"))
                .addTextFile("test.mixins.json", """
                        {
                            "package": "test.mixin",
                            "mixins": ["RequiresFrameRecompute"]
                        }
                        """)
                .addClass("test.target.Super", """
                        public class Super {}
                        """)
                .addClass("test.target.IFace", """
                        public interface IFace {}
                        """)
                .addClass("test.target.Target", """
                        import net.neoforged.fml.common.Mod;

                        @Mod("test")
                        public class Target {
                            {
                                test(null);
                            }

                            public static void test(Super obj) {}
                        }
                        """)
                .addClass("test.mixin.RequiresFrameRecompute", """
                        import org.spongepowered.asm.mixin.injection.At;
                        import org.spongepowered.asm.mixin.injection.Inject;
                        import org.spongepowered.asm.mixin.Mixin;
                        import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
                        import test.target.Super;

                        @Mixin(test.target.Target.class)
                        public class RequiresFrameRecompute implements test.target.IFace {
                            @Inject(
                                method = "test",
                                at = @At("HEAD")
                            )
                            private static void testInject(Super obj, CallbackInfo ci) {
                                // makes recomputing the stack necessary
                                takesLocalType(alwaysFalse() ? obj : new Super() {});
                            }

                            private static boolean alwaysFalse() {
                                return false;
                            }

                            private static void takesLocalType(Super object) {}
                        }
                        """)
                .build();

        var result = launchAndLoad("neoforgeclient");
        var testClass = result.launchClassLoader().loadClass("test.target.Target");
        var injectedInterface = result.launchClassLoader().loadClass("test.target.IFace");
        assertTrue(injectedInterface.isAssignableFrom(testClass));
    }
}
