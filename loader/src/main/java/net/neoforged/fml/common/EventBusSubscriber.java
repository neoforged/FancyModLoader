/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;

// @formatter:off - spotless doesn't like @
/**
 * Annotate a class which will be subscribed to an Event Bus at mod construction time. Defaults to subscribing the current modid to the {@code NeoForge#EVENT_BUS} on both sides.
 *
 * <p>Annotated classes will be scanned for <b>static</b> methods that have the {@link SubscribeEvent} annotation.
 * For example:
 *
 * {@snippet :
 * @EventBusSubscriber
 * public class MyEventHandler {
 *     @SubscribeEvent
 *     private static void onSomeEvent(SomeEvent event) {
 *         // SomeEvent handler here
 *     }
 *
 *     @SubscribeEvent
 *     private static void onAnotherEvent(AnotherEvent event) {
 *         // AnotherEvent handler here
 *     }
 * }
 * }
 *
 * @see Bus
 */
// @formatter:on
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    /**
     * Specify targets to load this event subscriber on. Can be used to avoid loading Client specific events on a dedicated server, for example.
     *
     * @return an array of Dist to load this event subscriber on
     */
    Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER };

    /**
     * Optional value, only necessary if this annotation is not on the same class that has a @Mod annotation. Needed to prevent early classloading of classes not owned by your mod.
     *
     * @return a modid
     */
    String modid() default "";

    /**
     * Specify an alternative bus to listen to
     *
     * @return the bus you wish to listen to
     */
    Bus bus() default Bus.GAME;

    enum Bus {
        /**
         * The main NeoForge Event Bus, used after the game has started up.
         *
         * <p>See {@code NeoForge#EVENT_BUS}</p>
         */
        GAME,
        /**
         * The mod-specific Event bus, used during startup.
         *
         * @see ModContainer#getEventBus()
         */
        MOD,
    }
}
