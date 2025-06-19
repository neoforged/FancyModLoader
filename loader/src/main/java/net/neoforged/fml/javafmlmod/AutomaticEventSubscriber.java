/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.Bindings;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.neoforged.fml.Logging.LOADING;

/**
 * Automatic eventbus subscriber - reads {@link EventBusSubscriber}
 * annotations and passes the class instances to the {@link EventBusSubscriber.Bus}
 * defined by the annotation. Defaults to {@code NeoForge#EVENT_BUS}
 */
public class AutomaticEventSubscriber {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Type AUTO_SUBSCRIBER = Type.getType(EventBusSubscriber.class);
    private static final Type MOD_TYPE = Type.getType(Mod.class);

    @SuppressWarnings("removal")
    public static void inject(final ModContainer mod, final ModFileScanData scanData, final Module layer) {
        if (scanData == null) return;
        LOGGER.debug(LOADING, "Attempting to inject @EventBusSubscriber classes into the eventbus for {}", mod.getModId());
        List<ModFileScanData.AnnotationData> ebsTargets = scanData.getAnnotations().stream().filter(annotationData -> AUTO_SUBSCRIBER.equals(annotationData.annotationType())).collect(Collectors.toList());
        Map<String, String> modids = scanData.getAnnotations().stream().filter(annotationData -> MOD_TYPE.equals(annotationData.annotationType())).collect(Collectors.toMap(a -> a.clazz().getClassName(), a -> (String) a.annotationData().get("value")));

        ebsTargets.forEach(ad -> {
            final EnumSet<Dist> sides = getSides(ad.annotationData().get("value"));
            final String modId = (String) ad.annotationData().getOrDefault("modid", modids.getOrDefault(ad.clazz().getClassName(), mod.getModId()));
            final ModAnnotation.EnumHolder busTargetHolder = (ModAnnotation.EnumHolder) ad.annotationData().getOrDefault("bus", new ModAnnotation.EnumHolder(null, EventBusSubscriber.Bus.BOTH.name()));
            final EventBusSubscriber.Bus busTarget = EventBusSubscriber.Bus.valueOf(busTargetHolder.value());
            if (Objects.equals(mod.getModId(), modId) && sides.contains(FMLEnvironment.dist)) {
                try {
                    if (busTarget == EventBusSubscriber.Bus.BOTH) {
                        LOGGER.debug(LOADING, "Scanning class {} for @SubscribeEvent-annotated methods", ad.clazz().getClassName());
                        var clazz = Class.forName(ad.clazz().getClassName(), true, layer.getClassLoader());

                        var modBusListeners = new ArrayList<Method>();
                        var gameBusListeners = new ArrayList<Method>();

                        for (Method method : clazz.getDeclaredMethods()) {
                            if (!method.isAnnotationPresent(SubscribeEvent.class)) {
                                continue;
                            }

                            if (!Modifier.isStatic(method.getModifiers())) {
                                throw new IllegalArgumentException("Method " + method + " annotated with @SubscribeEvent is not static");
                            }

                            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                throw new IllegalArgumentException("Method " + method + " annotated with @SubscribeEvent must have only one parameter that is an Event subtype");
                            }

                            var eventType = method.getParameterTypes()[0];
                            if (IModBusEvent.class.isAssignableFrom(eventType)) {
                                modBusListeners.add(method);
                            } else {
                                gameBusListeners.add(method);
                            }
                        }

                        // Preserve old behaviour for backwards compat - when there is not a mix of listener types
                        // register the class directly to the bus so that IEventBus#unregister can be called with the class
                        // in order to unregister all @SubscribeEvent-annotated listeners inside it
                        if (modBusListeners.isEmpty()) {
                            LOGGER.debug("Subscribing @EventBusSubscriber class {} to the game event bus", ad.clazz());
                            Bindings.getGameBus().register(clazz);
                        } else {
                            if (gameBusListeners.isEmpty()) {
                                var modBus = mod.getEventBus();
                                if (modBus != null) {
                                    LOGGER.debug("Subscribing @EventBusSubscriber class {} to the mod event bus of mod {}", ad.clazz(), mod.getModId());
                                    modBus.register(clazz);
                                }
                            } else {
                                LOGGER.debug(LOADING, "Found mix of game bus and mod bus listeners in @EventBusSubscriber class {}, registering them separately", ad.clazz());
                                for (var method : modBusListeners) {
                                    var modBus = mod.getEventBus();
                                    if (modBus == null) {
                                        throw new IllegalArgumentException("Method " + method + " attempted to register a mod bus event, but mod " + mod.getModId() + " has no event bus");
                                    } else {
                                        LOGGER.debug(LOADING, "Subscribing method {} to the event bus of mod {}", method, mod.getModId());
                                        modBus.register(method);
                                    }
                                }

                                for (var method : gameBusListeners) {
                                    LOGGER.debug(LOADING, "Subscribing method {} to the game event bus", method);
                                    Bindings.getGameBus().register(method);
                                }
                            }
                        }
                    } else {
                        IEventBus bus = switch (busTarget) {
                            case GAME -> Bindings.getGameBus();
                            case MOD -> mod.getEventBus();
                            default -> null;
                        };

                        if (bus != null) {
                            LOGGER.debug(LOADING, "Auto-subscribing {} to {}", ad.clazz().getClassName(), busTarget);

                            bus.register(Class.forName(ad.clazz().getClassName(), true, layer.getClassLoader()));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.fatal(LOADING, "Failed to register class {} with @EventBusSubscriber annotation", ad.clazz(), e);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static EnumSet<Dist> getSides(Object data) {
        if (data == null) {
            return EnumSet.allOf(Dist.class);
        } else {
            return ((List<ModAnnotation.EnumHolder>) data).stream().map(eh -> Dist.valueOf(eh.value())).collect(Collectors.toCollection(() -> EnumSet.noneOf(Dist.class)));
        }
    }
}
