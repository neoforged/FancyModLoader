/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.util;

import com.google.common.collect.Streams;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IOrderedProvider;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class ServiceLoaderUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceLoaderUtil.class);

    private ServiceLoaderUtil() {}

    public static <T> List<T> loadServices(ILaunchContext context, Class<T> serviceClass) {
        return loadServices(context, serviceClass, List.of());
    }

    /**
     * @param serviceClass If the service class implements {@link IOrderedProvider}, the services will automatically be sorted.
     */
    public static <T> List<T> loadServices(ILaunchContext context, Class<T> serviceClass, Collection<T> additionalServices) {
        var serviceLoaderServices = context.loadServices(serviceClass).map(p -> {
            try {
                return p.get();
            } catch (ServiceConfigurationError sce) {
                LOGGER.error("Failed to load implementation for {}", serviceClass, sce);
                return null;
            }
        }).filter(Objects::nonNull);

        var servicesStream = Streams.concat(additionalServices.stream(), serviceLoaderServices).distinct();

        var applyPriority = IOrderedProvider.class.isAssignableFrom(serviceClass);
        if (applyPriority) {
            servicesStream = servicesStream.sorted(Comparator.comparingInt(service -> ((IOrderedProvider) service).getPriority()).reversed());
        }

        var services = servicesStream.toList();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(LogMarkers.CORE, "Found {} implementations of {}:", services.size(), serviceClass.getSimpleName());
            for (T service : services) {
                String priorityPrefix = "";
                if (applyPriority) {
                    priorityPrefix = String.format(Locale.ROOT, "%8d - ", ((IOrderedProvider) service).getPriority());
                }

                if (LOGGER.isDebugEnabled(LogMarkers.CORE)) {
                    if (additionalServices.contains(service)) {
                        LOGGER.debug(LogMarkers.CORE, "\t{}[built-in] {}", priorityPrefix, identifyService(context, service));
                    } else {
                        LOGGER.debug(LogMarkers.CORE, "\t{}{}", priorityPrefix, identifyService(context, service));
                    }
                }
            }
        }

        return services;
    }

    private static String identifyService(ILaunchContext context, Object o) {
        var sourcePath = identifySourcePath(context, o);
        return o.getClass().getName() + " from " + sourcePath;
    }

    /**
     * Given any object, this method tries to build a human-readable chain of paths that identify where the
     * code implementing the given object is coming from.
     */
    public static String identifySourcePath(ILaunchContext context, Object object) {
        var codeLocation = object.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (codeLocation == null) {
            return "unknown";
        }
        Path primaryPath;
        try {
            primaryPath = Paths.get(codeLocation.toURI());
        } catch (URISyntaxException e) {
            return codeLocation.toString();
        }
        if (LoadingModList.get() != null) {
            for (var plugin : LoadingModList.get().getPlugins()) {
                if (plugin.getFile().getContents().getPrimaryPath().equals(primaryPath)) {
                    return plugin.getFile().toString();
                }
            }
            for (var gameLibrary : LoadingModList.get().getGameLibraries()) {
                if (gameLibrary.getContents().getPrimaryPath().equals(primaryPath)) {
                    return gameLibrary.toString();
                }
            }
            for (var modFile : LoadingModList.get().getModFiles()) {
                if (modFile.getFile().getContents().getPrimaryPath().equals(primaryPath)) {
                    return modFile.getFile().toString();
                }
            }
        }

        return codeLocation.toString();
    }
}
