/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import static net.neoforged.fml.Logging.LOADING;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.Bindings;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.DependsOn;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.IModInfo;
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
    private static final Type DEPENDS_ON_LIST_TYPE = Type.getType(DependsOn.class);

    public static void inject(final ModContainer mod, final ModFileScanData scanData, final Module layer) {
        if (scanData == null) return;
        LOGGER.debug(LOADING, "Attempting to inject @EventBusSubscriber classes into the eventbus for {}", mod.getModId());
        List<ModFileScanData.AnnotationData> ebsTargets = scanData.getAnnotations().stream().filter(annotationData -> AUTO_SUBSCRIBER.equals(annotationData.annotationType())).collect(Collectors.toList());
        Map<String, String> modids = scanData.getAnnotations().stream().filter(annotationData -> MOD_TYPE.equals(annotationData.annotationType())).collect(Collectors.toMap(a -> a.clazz().getClassName(), a -> (String) a.annotationData().get("value")));

        ebsTargets.forEach(ad -> {
            @SuppressWarnings("unchecked")
            final EnumSet<Dist> sides = getSides(ad.annotationData().get("value"));
            final String modId = (String) ad.annotationData().getOrDefault("modid", modids.getOrDefault(ad.clazz().getClassName(), mod.getModId()));
            final ModAnnotation.EnumHolder busTargetHolder = (ModAnnotation.EnumHolder) ad.annotationData().getOrDefault("bus", new ModAnnotation.EnumHolder(null, EventBusSubscriber.Bus.GAME.name()));
            final EventBusSubscriber.Bus busTarget = EventBusSubscriber.Bus.valueOf(busTargetHolder.value());
            final List<ModFileScanData.AnnotationData> depData = scanData.getAnnotations().stream()
                    .filter(annotationData -> DEPENDS_ON_LIST_TYPE.equals(annotationData.annotationType()) && ad.clazz().equals(annotationData.clazz())).toList();

            final Set<String> loadedModIds = ModList.get().getMods().stream().map(IModInfo::getModId).collect(Collectors.toSet());

            final boolean allDepsPresent;
            if (depData.isEmpty()) allDepsPresent = true;
            else {
                var deps = (String[]) depData.getFirst().annotationData().getOrDefault("value", new DependsOn[0]);
                allDepsPresent = loadedModIds.containsAll(List.of(deps));
            }

            if (Objects.equals(mod.getModId(), modId) && sides.contains(FMLEnvironment.dist) && allDepsPresent) {
                try {
                    IEventBus bus = switch (busTarget) {
                        case GAME -> Bindings.getGameBus();
                        case MOD -> mod.getEventBus();
                    };

                    if (bus != null) {
                        LOGGER.debug(LOADING, "Auto-subscribing {} to {}", ad.clazz().getClassName(), busTarget);

                        bus.register(Class.forName(ad.clazz().getClassName(), true, layer.getClassLoader()));
                    }
                } catch (Exception e) {
                    LOGGER.fatal(LOADING, "Failed to register class {} with @EventBusSubscriber annotation", ad.clazz(), e);
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
