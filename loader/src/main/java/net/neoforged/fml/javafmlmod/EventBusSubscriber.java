package net.neoforged.fml.javafmlmod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.Bindings;
import net.neoforged.fml.ModContainer;

/**
 * Annotate a class which will be subscribed to an Event Bus at mod construction time.
 * Defaults to subscribing the current modid to the {@code NeoForge#EVENT_BUS}
 * on both sides.
 *
 * @see Bus
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface EventBusSubscriber {
    /**
     * Specify targets to load this event subscriber on. Can be used to avoid loading Client specific events
     * on a dedicated server, for example.
     *
     * @return an array of Dist to load this event subscriber on
     */
    Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER };

    /**
     * Optional value, only necessary if this annotation is not on the same class that has a @Mod annotation.
     * Needed to prevent early classloading of classes not owned by your mod.
     *
     * @return a modid
     */
    String modid() default "";

    /**
     * Specify an alternative bus to listen to
     *
     * @return the bus you wish to listen to
     */
    Bus bus() default Bus.FORGE;

    enum Bus {
        /**
         * The main Forge Event Bus.
         *
         * <p>See {@code NeoForge#EVENT_BUS}</p>
         */
        FORGE(Bindings.getForgeBus()),
        /**
         * The mod specific Event bus.
         *
         * @see ModContainer#getEventBus()
         */
        MOD(() -> FMLJavaModLoadingContext.get().getModEventBus());

        private final Supplier<IEventBus> busSupplier;

        Bus(final Supplier<IEventBus> eventBusSupplier) {
            this.busSupplier = eventBusSupplier;
        }

        @Deprecated(forRemoval = true)
        public Supplier<IEventBus> bus() {
            return busSupplier;
        }
    }
}
