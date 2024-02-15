/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import static net.neoforged.fml.Logging.LOADING;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.Bindings;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.moddiscovery.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

/**
 * Automatic eventbus subscriber - reads {@link Mod.EventBusSubscriber}
 * annotations and passes the class instances to the {@link Mod.EventBusSubscriber.Bus}
 * defined by the annotation. Defaults to {@code NeoForge#EVENT_BUS}
 */
public class AutomaticEventSubscriber {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Type AUTO_SUBSCRIBER = Type.getType(Mod.EventBusSubscriber.class);
    private static final Type MOD_TYPE = Type.getType(Mod.class);

    public static void inject(final ModContainer mod, final ModFileScanData scanData, final ClassLoader loader) {
        if (scanData == null) return;
        LOGGER.debug(LOADING, "Attempting to inject @EventBusSubscriber classes into the eventbus for {}", mod.getModId());
        List<ModFileScanData.AnnotationData> ebsTargets = scanData.getAnnotations().stream().filter(annotationData -> AUTO_SUBSCRIBER.equals(annotationData.annotationType())).collect(Collectors.toList());
        Map<String, String> modids = scanData.getAnnotations().stream().filter(annotationData -> MOD_TYPE.equals(annotationData.annotationType())).collect(Collectors.toMap(a -> a.clazz().getClassName(), a -> (String) a.annotationData().get("value")));

        ebsTargets.forEach(ad -> {
            @SuppressWarnings("unchecked")
            final List<ModAnnotation.EnumHolder> sidesValue = (List<ModAnnotation.EnumHolder>) ad.annotationData().getOrDefault("value", Arrays.asList(new ModAnnotation.EnumHolder(null, "CLIENT"), new ModAnnotation.EnumHolder(null, "DEDICATED_SERVER")));
            final EnumSet<Dist> sides = sidesValue.stream().map(eh -> Dist.valueOf(eh.getValue())).collect(Collectors.toCollection(() -> EnumSet.noneOf(Dist.class)));
            final String modId = (String) ad.annotationData().getOrDefault("modid", modids.getOrDefault(ad.clazz().getClassName(), mod.getModId()));
            final ModAnnotation.EnumHolder busTargetHolder = (ModAnnotation.EnumHolder) ad.annotationData().getOrDefault("bus", new ModAnnotation.EnumHolder(null, "FORGE"));
            final Mod.EventBusSubscriber.Bus busTarget = Mod.EventBusSubscriber.Bus.valueOf(busTargetHolder.getValue());
            if (Objects.equals(mod.getModId(), modId) && sides.contains(FMLEnvironment.dist)) {
                try {
                    IEventBus bus = switch (busTarget) {
                        case FORGE -> Bindings.getForgeBus().get();
                        case MOD -> mod.getEventBus();
                    };

                    if (bus != null) {
                        LOGGER.debug(LOADING, "Auto-subscribing {} to {}", ad.clazz().getClassName(), busTarget);

                        bus.register(Class.forName(ad.clazz().getClassName(), true, loader));
                    }
                } catch (ClassNotFoundException e) {
                    LOGGER.fatal(LOADING, "Failed to load mod class {} for @EventBusSubscriber annotation", ad.clazz(), e);
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
