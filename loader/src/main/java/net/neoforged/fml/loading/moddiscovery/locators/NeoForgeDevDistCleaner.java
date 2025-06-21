/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Masks classes from the Minecraft jar that are for the wrong distribution in a development environment, throwing an
 * informative exception.
 */
@ApiStatus.Internal
public class NeoForgeDevDistCleaner implements ILaunchPluginService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker DISTXFORM = MarkerFactory.getMarker("DISTXFORM");
    private static final EnumSet<Phase> EMPTY = EnumSet.noneOf(Phase.class);

    private Dist dist;

    private final Set<String> maskedClasses = new HashSet<>();

    @Override
    public String name() {
        return "neoforgedevdistcleaner";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        if (maskedClasses.contains(classType.getClassName())) {
            String message = String.format("Attempted to load class %s which is not present on the %s", classType.getClassName(), switch (dist) {
                case CLIENT -> "client";
                case DEDICATED_SERVER -> "dedicated server";
            });
            LOGGER.error(DISTXFORM, message);
            // We must sneakily throw a (usually checked) ClassNotFoundException here. This is necessary so that java's
            // initialization logic produces a NoClassDefFoundError in dev, like it would in prod, when a class
            // referencing such a class is loaded. Though this should not often matter, as errors cannot generally be
            // caught in a recoverable fashion, they may still be caught for debugging purposes or the like so it is
            // best to be consistent here.
            throwUnchecked(new ClassNotFoundException(message));
        }
        return EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static <T, X extends Throwable> void throwUnchecked(T throwable) throws X {
        throw (X) throwable;
    }

    public synchronized void maskClasses(Collection<String> classes) {
        maskedClasses.addAll(classes);
    }

    public void setDistribution(Dist dist) {
        this.dist = Objects.requireNonNull(dist);
    }
}
