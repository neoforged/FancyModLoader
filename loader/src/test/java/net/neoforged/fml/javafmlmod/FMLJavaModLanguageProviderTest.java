/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.LauncherTest;
import net.neoforged.fml.loading.SimulatedInstallation;
import net.neoforged.fml.test.RuntimeCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FMLJavaModLanguageProviderTest extends LauncherTest {
    // Allows us to capture events received by mods loaded in the game layer
    public static final List<FMLClientSetupEvent> EVENTS = new ArrayList<>();
    public static final List<String> MESSAGES = new ArrayList<>();

    @AfterEach
    void tearDown() {
        EVENTS.clear();
        MESSAGES.clear();
    }

    /**
     * Tests that the java language provider warns about classes that are annotated with @Mod,
     * but use a mod-id that is not declared in the mod-file.
     */
    @Test
    public void testDanglingEntryPoints() throws Exception {
        installation.setupProductionClient();

        var testJar = installation.writeModJar("test.jar", SimulatedInstallation.createModsToml("testmod", "1.0"));
        try (var compiler = RuntimeCompiler.create(testJar)) {
            compiler.builder()
                    .addClass("testmod.DanglingEntryPoint", """
                            package testmod;
                            @net.neoforged.fml.common.Mod("notthismod")
                            class DanglingEntryPoint {
                            }
                            """)
                    .compile();
        }

        var e = Assertions.assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues()))
                .containsOnly("ERROR: File mods/test.jar contains mod entrypoint class testmod.DanglingEntryPoint for mod with id notthismod, which does not exist or is not in the same file."
                        + "\nDid you forget to update the mod id in the entrypoint?");
    }

    @Test
    void testModConstructionWithoutPublicConstructor() throws Exception {
        installation.setupProductionClient();

        var testJar = installation.writeModJar("test.jar", SimulatedInstallation.createModsToml("testmod", "1.0"));
        try (var compiler = RuntimeCompiler.create(testJar)) {
            compiler.builder()
                    .addClass("testmod.EntryPoint", """
                            package testmod;
                            @net.neoforged.fml.common.Mod("testmod")
                            class EntryPoint {
                                EntryPoint() {
                                }
                            }
                            """)
                    .compile();
        }

        var e = Assertions.assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues()))
                .containsOnly("ERROR: testmod (testmod) has failed to load correctly"
                        + "\njava.lang.RuntimeException: Mod class class testmod.EntryPoint must have exactly 1 public constructor, found 0");
    }

    @Test
    void testModConstructionAndEventDispatch() throws Exception {
        installation.setupProductionClient();

        var testJar = installation.writeModJar("test.jar", SimulatedInstallation.createModsToml("testmod", "1.0"));
        try (var compiler = RuntimeCompiler.create(testJar)) {
            compiler.builder()
                    .addClass("testmod.EntryPoint", """
                            import java.util.ArrayList;
                            @net.neoforged.fml.common.Mod("testmod")
                            public class EntryPoint {
                                public EntryPoint(net.neoforged.bus.api.IEventBus modEventBus) {
                                    modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, e -> net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.EVENTS.add(e));
                                }
                            }
                            """)
                    .compile();
        }

        launchAndLoad("neoforgeclient");

        ModLoader.dispatchParallelEvent("test", Runnable::run, Runnable::run, () -> {}, FMLClientSetupEvent::new);

        assertThat(EVENTS).hasSize(1);
    }

    @Test
    void testMultipleEntrypoints() throws Exception {
        installation.setupProductionClient();

        var testJar = installation.writeModJar("test.jar", SimulatedInstallation.createModsToml("testmod", "1.0"));
        try (var compiler = RuntimeCompiler.create(testJar)) {
            compiler.builder()
                    .addClass("testmod.EntryPoint", """
                            @net.neoforged.fml.common.Mod("testmod")
                            public class EntryPoint {
                                public EntryPoint() {
                                    net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.MESSAGES.add("common");
                                }
                            }
                            """)
                    .addClass("testmod.ClientEntryPoint", """
                            @net.neoforged.fml.common.Mod(value = "testmod", dist = net.neoforged.api.distmarker.Dist.CLIENT)
                            public class ClientEntryPoint {
                                public ClientEntryPoint() {
                                    net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.MESSAGES.add("client");
                                }
                            }
                            """)
                    .compile();
        }

        launchAndLoad("neoforgeclient");

        assertThat(MESSAGES).isEqualTo(List.of("common", "client"));
    }

    @Test
    void testErrorDuringEventDispatch() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("test.jar")
                .withModsToml(builder -> {
                    builder.unlicensedJavaMod().addMod("testmod", "1.0");
                })
                .addClass("testmod.EntryPoint", """
                        import java.util.ArrayList;
                        @net.neoforged.fml.common.Mod("testmod")
                        public class EntryPoint {
                            public EntryPoint(net.neoforged.bus.api.IEventBus modEventBus) {
                                modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, e -> {
                                    throw new RuntimeException();
                                });
                            }
                        }
                        """)
                .build();

        launchAndLoad("neoforgeclient");

        var e = Assertions.assertThrows(ModLoadingException.class, () -> {
            ModLoader.dispatchParallelEvent("test", Runnable::run, Runnable::run, () -> {}, FMLClientSetupEvent::new);
        });
        assertThat(getTranslatedIssues(e.getIssues())).containsOnly("ERROR: testmod (testmod) encountered an error while dispatching the net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event"
                + "\njava.lang.RuntimeException: null");
    }
}
