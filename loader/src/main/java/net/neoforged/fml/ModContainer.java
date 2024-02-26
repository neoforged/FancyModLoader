/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static net.neoforged.fml.Logging.LOADING;

/**
 * The container that wraps around mods in the system.
 * <p>
 * The philosophy is that individual mod implementation technologies should not
 * impact the actual loading and management of mod code. This class provides
 * a mechanism by which we can wrap actual mod code so that the loader and other
 * facilities can treat mods at arms length.
 * </p>
 *
 * @author cpw
 *
 */

public abstract class ModContainer
{
    private static final Logger LOGGER = LogManager.getLogger();

    protected final String modId;
    protected final String namespace;
    protected final IModInfo modInfo;
    protected ModLoadingStage modLoadingStage;
    protected Supplier<?> contextExtension;
    protected final Map<ModLoadingStage, Runnable> activityMap = new HashMap<>();
    protected final Map<Class<? extends IExtensionPoint<?>>, Supplier<?>> extensionPoints = new IdentityHashMap<>();
    protected final EnumMap<ModConfig.Type, ModConfig> configs = new EnumMap<>(ModConfig.Type.class);

    public ModContainer(IModInfo info)
    {
        this.modId = info.getModId();
        // TODO: Currently not reading namespace from configuration..
        this.namespace = this.modId;
        this.modInfo = info;
        this.modLoadingStage = ModLoadingStage.CONSTRUCT;

        final String displayTestString = info.getConfig().<String>getConfigElement("displayTest").orElse("MATCH_VERSION"); // missing defaults to DEFAULT type
        Supplier<IExtensionPoint.DisplayTest> displayTestSupplier = switch (displayTestString) {
            case "MATCH_VERSION" -> // default displaytest checks for version string match
                    () -> new IExtensionPoint.DisplayTest(() -> this.modInfo.getVersion().toString(),
                        (incoming, isNetwork) -> Objects.equals(incoming, this.modInfo.getVersion().toString()));
            case "IGNORE_SERVER_VERSION" -> // Ignores any version information coming from the server - use for server only mods
                    () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (incoming, isNetwork) -> true);
            case "IGNORE_ALL_VERSION" -> // Ignores all information and provides no information
                    () -> new IExtensionPoint.DisplayTest(() -> "", (incoming, isNetwork) -> true);
            case "NONE" -> null; // NO display test at all - use this if you're going to do your own display test
            default -> // any other value throws an exception
                    throw new IllegalArgumentException("Invalid displayTest value supplied in mods.toml");
        };
        if (displayTestSupplier != null)
            registerExtensionPoint(IExtensionPoint.DisplayTest.class, displayTestSupplier);
        else
            extensionPoints.remove(IExtensionPoint.DisplayTest.class);
    }

    /**
     * Errored container state, used for filtering. Does nothing.
     */
    ModContainer()
    {
        this.modLoadingStage = ModLoadingStage.ERROR;
        modId = "BROKEN";
        namespace = "BROKEN";
        modInfo = null;
    }
    /**
     * @return the modid for this mod
     */
    public final String getModId()
    {
        return modId;
    }

    /**
     * @return the resource prefix for the mod
     */
    public final String getNamespace()
    {
        return namespace;
    }

    /**
     * @return The current loading stage for this mod
     */
    public ModLoadingStage getCurrentState()
    {
        return modLoadingStage;
    }

    public static <T extends Event & IModBusEvent> CompletableFuture<Void> buildTransitionHandler(
            final ModContainer target,
            final IModStateTransition.EventGenerator<T> eventGenerator,
            final ProgressMeter progressBar,
            final BiFunction<ModLoadingStage, Throwable, ModLoadingStage> stateChangeHandler,
            final Executor executor) {
        return CompletableFuture
                .runAsync(() -> {
                    ModLoadingContext.get().setActiveContainer(target);
                    target.activityMap.getOrDefault(target.modLoadingStage, ()->{}).run();
                    target.acceptEvent(eventGenerator.apply(target));
                }, executor)
                .whenComplete((mc, exception) -> {
                    target.modLoadingStage = stateChangeHandler.apply(target.modLoadingStage, exception);
                    progressBar.increment();
                    ModLoadingContext.get().setActiveContainer(null);
                });
    }

    public IModInfo getModInfo()
    {
        return modInfo;
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> Optional<T> getCustomExtension(Class<? extends IExtensionPoint<T>> point) {
        return Optional.ofNullable((T)extensionPoints.getOrDefault(point,()-> null).get());
    }

    public <T extends Record & IExtensionPoint<T>> void registerExtensionPoint(Class<? extends IExtensionPoint<T>> point, Supplier<T> extension)
    {
        extensionPoints.put(point, extension);
    }

    public void addConfig(final ModConfig modConfig) {
       configs.put(modConfig.getType(), modConfig);
    }

    /**
     * Does this mod match the supplied mod?
     *
     * @param mod to compare
     * @return if the mod matches
     */
    public abstract boolean matches(Object mod);

    /**
     * @return the mod object instance
     */
    public abstract Object getMod();

    /**
     * {@return the event bus for this mod, if available}
     *
     * <p>Not all mods have an event bus!
     *
     * @implNote For custom mod container implementations, the event bus must be built with
     * {@link BusBuilder#allowPerPhasePost()} or posting via {@link #acceptEvent(EventPriority, Event)} will throw!
     */
    @Nullable
    public abstract IEventBus getEventBus();

    /**
     * Accept an arbitrary event for processing by the mod. Posted to {@link #getEventBus()}.
     * @param e Event to accept
     */
    protected final <T extends Event & IModBusEvent> void acceptEvent(T e) {
        IEventBus bus = getEventBus();
        if (bus == null) return;

        try {
            LOGGER.trace(LOADING, "Firing event for modid {} : {}", this.getModId(), e);
            bus.post(e);
            LOGGER.trace(LOADING, "Fired event for modid {} : {}", this.getModId(), e);
        } catch (Throwable t) {
            LOGGER.error(LOADING,"Caught exception during event {} dispatch for modid {}", e, this.getModId(), t);
            throw new ModLoadingException(modInfo, modLoadingStage, "fml.modloading.errorduringevent", t);
        }
    }

    /**
     * Accept an arbitrary event for processing by the mod. Posted to {@link #getEventBus()}.
     * @param e Event to accept
     */
    protected final <T extends Event & IModBusEvent> void acceptEvent(EventPriority phase, T e) {
        IEventBus bus = getEventBus();
        if (bus == null) return;

        try {
            LOGGER.trace(LOADING, "Firing event for phase {} for modid {} : {}", phase, this.getModId(), e);
            bus.post(phase, e);
            LOGGER.trace(LOADING, "Fired event for phase {} for modid {} : {}", phase, this.getModId(), e);
        } catch (Throwable t) {
            LOGGER.error(LOADING,"Caught exception during event {} dispatch for modid {}", e, this.getModId(), t);
            throw new ModLoadingException(modInfo, modLoadingStage, "fml.modloading.errorduringevent", t);
        }
    }
}
