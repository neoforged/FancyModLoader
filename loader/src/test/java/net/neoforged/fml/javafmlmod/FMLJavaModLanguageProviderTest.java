/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LauncherTest;
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

        installation.buildModJar("test.jar")
                .withTestmodModsToml()
                .addClass("testmod.DanglingEntryPoint", """
                        @net.neoforged.fml.common.Mod("notthismod")
                        class DanglingEntryPoint {
                        }
                        """)
                .build();

        var e = Assertions.assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues()))
                .containsOnly("ERROR: File mods/test.jar contains mod entrypoint class testmod.DanglingEntryPoint for mod with id notthismod, which does not exist or is not in the same file."
                        + "\nDid you forget to update the mod id in the entrypoint?");
    }

    @Test
    void testModConstructionWithoutPublicConstructor() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("test.jar")
                .withTestmodModsToml()
                .addClass("testmod.EntryPoint", """
                        @net.neoforged.fml.common.Mod("testmod")
                        class EntryPoint {
                            EntryPoint() {
                            }
                        }
                        """)
                .build();

        var e = Assertions.assertThrows(ModLoadingException.class, () -> launchAndLoad("neoforgeclient"));
        assertThat(getTranslatedIssues(e.getIssues()))
                .containsOnly("ERROR: testmod (testmod) has failed to load correctly"
                        + "\njava.lang.RuntimeException: Mod class class testmod.EntryPoint must have exactly 1 public constructor, found 0");
    }

    @Test
    void testModConstructionAndEventDispatch() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("test.jar")
                .withTestmodModsToml()
                .addClass("testmod.EntryPoint", """
                        import java.util.ArrayList;
                        @net.neoforged.fml.common.Mod("testmod")
                        public class EntryPoint {
                            public EntryPoint(net.neoforged.bus.api.IEventBus modEventBus) {
                                modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, e -> net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.EVENTS.add(e));
                            }
                        }
                        """)
                .build();

        launchAndLoad("neoforgeclient");

        ModLoader.dispatchParallelEvent("test", Runnable::run, Runnable::run, () -> {}, FMLClientSetupEvent::new);

        assertThat(EVENTS).hasSize(1);
    }

    @Test
    void testMultipleEntrypoints() throws Exception {
        installation.setupProductionClient();

        var testJar = installation.buildModJar("test.jar")
                .withTestmodModsToml()
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

                .build();

        launchAndLoad("neoforgeclient");

        assertThat(MESSAGES).isEqualTo(List.of("common", "client"));
    }

    @Test
    void testDependsEntrypointDoesntFire() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("test.jar")
                .withTestmodModsToml()
                .addClass("testmod.DependsEntryPoint", """
                        @net.neoforged.fml.common.Mod(value = "testmod", depends = "othermod")
                        public class DependsEntryPoint {
                            public DependsEntryPoint() {
                        		net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.MESSAGES.add("fired");
                            }
                        }
                        """)
                .build();

        launchAndLoad("neoforgeclient");

	    assertThat(MESSAGES).isEmpty();
    }

    @Test
    void testDependsEntrypointOrdering() throws Exception {
        installation.setupProductionClient();

        installation.buildModJar("othermod.jar").withMod("othermod", "1.0").build();
        installation.buildModJar("test.jar")
                .withTestmodModsToml(builder -> {
                    builder.addDependency("testmod", "othermod", "[1,)", config -> {
                        config.set("type", "optional");
                    });
                })
                .addClass("testmod.EntryPoint", """
                        @net.neoforged.fml.common.Mod("testmod")
                        public class EntryPoint {
                            public EntryPoint() {
                                net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.MESSAGES.add("common");
                            }
                        }
                        """)
                .addClass("testmod.DependsEntryPoint", """
                        @net.neoforged.fml.common.Mod(value = "testmod", depends = "othermod")
                        public class DependsEntryPoint {
                            public DependsEntryPoint() {
                                net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest.MESSAGES.add("dependency");
                            }
                        }
                        """)
                .build();

        launchAndLoad("neoforgeclient");

        assertThat(MESSAGES).isEqualTo(List.of("common", "dependency"));
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

    @Test
    void testEventBusSubscriber() throws Exception {
        installation.setupProductionClient();
        installation.buildModJar("test.jar")
                .withModsToml(builder -> builder.unlicensedJavaMod().addMod("testmod", "1.0"))
                .addClass("testmod.Subscriber", """
                        import net.neoforged.bus.api.SubscribeEvent;
                        import net.neoforged.fml.common.EventBusSubscriber;
                        import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

                        import net.neoforged.fml.javafmlmod.FMLJavaModLanguageProviderTest;

                        @EventBusSubscriber
                        public class Subscriber {
                            @SubscribeEvent
                            static void onConstruct(FMLConstructModEvent event) {
                                FMLJavaModLanguageProviderTest.MESSAGES.add("mod event bus event was fired!");
                            }

                            @SubscribeEvent
                            static void onTestEvent(FMLJavaModLanguageProviderTest.TestEvent event) {
                                event.message = "game event bus event was fired!";
                            }
                        }
                        """)
                .build();

        launchAndLoad("neoforgeclient");

        assertThat(MESSAGES).containsExactly("mod event bus event was fired!");

        final var event = new TestEvent();
        FMLLoader.getCurrent().getBindings().getGameBus().post(event);
        assertThat(event.message).isEqualTo("game event bus event was fired!");
    }

    public static final class TestEvent extends Event {
        public String message;
    }
}
