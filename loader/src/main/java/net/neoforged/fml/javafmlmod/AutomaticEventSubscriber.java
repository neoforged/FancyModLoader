/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import static net.neoforged.fml.Logging.LOADING;

import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

/**
 * Automatic eventbus subscriber - reads {@link EventBusSubscriber}
 * annotations and passes the class instances to the {@link EventBusSubscriber.Bus}
 * defined by the annotation. Defaults to {@code NeoForge#EVENT_BUS}
 */
public class AutomaticEventSubscriber {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Type AUTO_SUBSCRIBER = Type.getType(EventBusSubscriber.class);
    private static final Type MOD_TYPE = Type.getType(Mod.class);

    public static void inject(final ModContainer mod, final ModFileScanData scanData, final Module layer) {
        if (scanData == null) return;
        LOGGER.debug(LOADING, "Attempting to inject @EventBusSubscriber classes into the eventbus for {}", mod.getModId());
        List<ModFileScanData.AnnotationData> ebsTargets = scanData.getAnnotations().stream().filter(annotationData -> AUTO_SUBSCRIBER.equals(annotationData.annotationType())).collect(Collectors.toList());
        Map<String, String> modids = scanData.getAnnotations().stream().filter(annotationData -> MOD_TYPE.equals(annotationData.annotationType())).collect(Collectors.toMap(a -> a.clazz().getClassName(), a -> (String) a.annotationData().get("value")));

        ebsTargets.forEach(ad -> {
            final EnumSet<Dist> sides = getSides(ad.annotationData().get("value"));
            final String modId = (String) ad.annotationData().getOrDefault("modid", modids.getOrDefault(ad.clazz().getClassName(), mod.getModId()));
            final ModAnnotation.EnumHolder busTargetHolder = (ModAnnotation.EnumHolder) ad.annotationData().getOrDefault("bus", new ModAnnotation.EnumHolder(null, EventBusSubscriber.Bus.AUTOMATIC.name()));
            final EventBusSubscriber.Bus busTarget = EventBusSubscriber.Bus.valueOf(busTargetHolder.value());
            if (Objects.equals(mod.getModId(), modId) && sides.contains(FMLEnvironment.dist)) {
                try {
                    var clazz = Class.forName(ad.clazz().getClassName(), true, layer.getClassLoader());

                    IEventBus bus = switch (busTarget) {
                        case GAME -> FMLLoader.getBindings().getGameBus();
                        case MOD -> mod.getEventBus();
                        case AUTOMATIC -> {
                            boolean hasGame = false, hasMod = false;
                            for (var method : clazz.getDeclaredMethods()) {
                                if (!Modifier.isStatic(method.getModifiers()) || !method.isAnnotationPresent(SubscribeEvent.class) || method.getParameterTypes().length != 1) {
                                    continue;
                                }

                                var type = method.getParameterTypes()[0];
                                if (IModBusEvent.class.isAssignableFrom(type)) {
                                    hasMod = true;
                                } else if (Event.class.isAssignableFrom(type)) {
                                    hasGame = true;
                                }
                            }

                            if (hasGame && hasMod) {
                                ModLoader.addLoadingIssue(ModLoadingIssue.error("fml.modloading.javafml.ebs.mixed_buses", clazz.getName()).withAffectedMod(mod.getModInfo()));
                                yield null;
                            }

                            // We'll let the bus do the rest of the checking (instance methods, or inheritance) to catch other errors
                            // if we haven't found any methods
                            yield hasMod ? mod.getEventBus() : FMLLoader.getBindings().getGameBus();
                        }
                    };

                    if (bus != null) {
                        LOGGER.debug(LOADING, "Auto-subscribing {} to the {} bus", ad.clazz().getClassName(), bus == mod.getEventBus() ? "mod" : "game");

                        bus.register(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    LOGGER.fatal(LOADING, "Failed to load mod class {} for @EventBusSubscriber annotation", ad.clazz(), e);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public static EnumSet<Dist> getSides(Object data) {
        if (data == null) {
            return EnumSet.allOf(Dist.class);
        } else {
            return ((List<ModAnnotation.EnumHolder>) data).stream().map(eh -> Dist.valueOf(eh.value())).collect(Collectors.toCollection(() -> EnumSet.noneOf(Dist.class)));
        }
    }
}
