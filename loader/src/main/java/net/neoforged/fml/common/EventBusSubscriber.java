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
import net.neoforged.fml.event.IModBusEvent;

// @formatter:off - spotless doesn't like @
/**
 * Annotate a class which will be subscribed to an Event Bus at mod construction time.
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
 * <p>
 * Event subscribers for events inheriting from {@link IModBusEvent} will be registered to the {@link ModContainer#getEventBus() mod's event bus},
 * while the rest will be registered to the {@code NeoForge#EVENT_BUS}.
 * <p>
 * <strong>Note:</strong> while {@link #bus()} still exists, this value is <i>ignored</i>.
 * <p>
 * By default, the subscribers will be registered on both physical sides. This can be customised using {@link #value()}.
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
     * Optional value, only necessary for mod jars that contain multiple mods.
     *
     * @return the mod id whose mod bus events to subscribe to
     */
    String modid() default "";

    /**
     * Specify an alternative bus to listen to
     *
     * @return the bus you wish to listen to
     * @deprecated This value is ignored, and the bus is determined automatically. Do not specify a bus at all as the option will be removed in later versions
     */
    @Deprecated(since = "1.21.1", forRemoval = true)
    Bus bus() default Bus.GAME;

    /**
     * @deprecated Prefer not specifying a bus at all, as {@linkplain EventBusSubscriber EventBusSubscriber-annotated classes}
     *             can now have listeners for either the mod event bus, the game event bus, or a mix of both.
     *             <p>
     *             Listeners whose event type inherits from {@link IModBusEvent} will be registered to the {@linkplain ModContainer#getEventBus() mod bus}
     *             while other listeners will be registered to the {@linkplain #GAME game bus}.
     */
    @Deprecated(since = "1.21.1", forRemoval = true)
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
        MOD;
    }
}
