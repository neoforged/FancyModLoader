/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import net.neoforged.bus.EventBusErrorMessage;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingStage;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FMLModContainer extends ModContainer
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker LOADING = MarkerManager.getMarker("LOADING");
    private final ModFileScanData scanResults;
    private final IEventBus eventBus;
    private Object modInstance;
    private final Class<?> modClass;

    public FMLModContainer(IModInfo info, String className, ModFileScanData modFileScanResults, ModuleLayer gameLayer)
    {
        super(info);
        LOGGER.debug(LOADING,"Creating FMLModContainer instance for {}", className);
        this.scanResults = modFileScanResults;
        activityMap.put(ModLoadingStage.CONSTRUCT, this::constructMod);
        this.eventBus = BusBuilder.builder()
                .setExceptionHandler(this::onEventFailed)
                .markerType(IModBusEvent.class)
                .allowPerPhasePost()
                .build();
        this.configHandler = Optional.of(ce->this.eventBus.post(ce.self()));
        final FMLJavaModLoadingContext contextExtension = new FMLJavaModLoadingContext(this);
        this.contextExtension = () -> contextExtension;
        try
        {
            var layer = gameLayer.findModule(info.getOwningFile().moduleName()).orElseThrow();
            modClass = Class.forName(layer, className);
            LOGGER.trace(LOADING,"Loaded modclass {} with {}", modClass.getName(), modClass.getClassLoader());
        }
        catch (Throwable e)
        {
            LOGGER.error(LOADING, "Failed to load class {}", className, e);
            throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
        }
    }

    private void onEventFailed(IEventBus iEventBus, Event event, EventListener[] iEventListeners, int i, Throwable throwable)
    {
        LOGGER.error(new EventBusErrorMessage(event, i, iEventListeners, throwable));
    }

    private void constructMod()
    {
        try
        {
            LOGGER.trace(LOADING, "Loading mod instance {} of type {}", getModId(), modClass.getName());
            try {
                // Try noargs constructor first
                this.modInstance = modClass.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException ignored) {
                // Otherwise look for constructor that can accept more arguments
                Map<Class<?>, Object> allowedConstructorArgs = Map.of(
                        IEventBus.class, eventBus,
                        ModContainer.class, this,
                        FMLModContainer.class, this);

                constructorsLoop: for (var constructor : modClass.getDeclaredConstructors()) {
                    var parameterTypes = constructor.getParameterTypes();
                    Object[] constructorArgs = new Object[parameterTypes.length];
                    Set<Class<?>> foundArgs = new HashSet<>();

                    for (int i = 0; i < parameterTypes.length; i++) {
                        Object argInstance = allowedConstructorArgs.get(parameterTypes[i]);
                        if (argInstance == null) {
                            // Unknown argument, try next constructor method...
                            continue constructorsLoop;
                        }

                        if (foundArgs.contains(parameterTypes[i])) {
                            throw new RuntimeException("Duplicate constructor argument type: " + parameterTypes[i]);
                        }

                        foundArgs.add(parameterTypes[i]);
                        constructorArgs[i] = argInstance;
                    }

                    // All arguments are found
                    this.modInstance = constructor.newInstance(constructorArgs);
                }

                if (this.modInstance == null) {
                    throw new RuntimeException("Could not find mod constructor. Allowed optional argument classes: " +
                            allowedConstructorArgs.keySet().stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
                }
            }
            LOGGER.trace(LOADING, "Loaded mod instance {} of type {}", getModId(), modClass.getName());
        }
        catch (Throwable e)
        {
            if (e instanceof InvocationTargetException) e = e.getCause(); // exceptions thrown when a reflected method call throws are wrapped in an InvocationTargetException. However, this isn't useful for the end user who has to dig through the logs to find the actual cause.
            LOGGER.error(LOADING,"Failed to create mod instance. ModID: {}, class {}", getModId(), modClass.getName(), e);
            throw new ModLoadingException(modInfo, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmod", e, modClass);
        }
        try {
            LOGGER.trace(LOADING, "Injecting Automatic event subscribers for {}", getModId());
            AutomaticEventSubscriber.inject(this, this.scanResults, this.modClass.getClassLoader());
            LOGGER.trace(LOADING, "Completed Automatic event subscribers for {}", getModId());
        } catch (Throwable e) {
            LOGGER.error(LOADING,"Failed to register automatic subscribers. ModID: {}, class {}", getModId(), modClass.getName(), e);
            throw new ModLoadingException(modInfo, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmod", e, modClass);
        }
    }

    @Override
    public boolean matches(Object mod)
    {
        return mod == modInstance;
    }

    @Override
    public Object getMod()
    {
        return modInstance;
    }

    @Override
    public IEventBus getEventBus()
    {
        return this.eventBus;
    }
}
