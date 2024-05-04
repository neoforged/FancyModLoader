/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.util;

import com.google.common.collect.Streams;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceConfigurationError;
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

                if (additionalServices.contains(service)) {
                    LOGGER.debug(LogMarkers.CORE, "\t{}[built-in] {}", priorityPrefix, identifyService(service));
                } else {
                    LOGGER.debug(LogMarkers.CORE, "\t{}{}", priorityPrefix, identifyService(service));
                }
            }
        }

        return services;
    }

    private static String identifyService(Object o) {
        String sourceFile;
        try {
            var sourcePath = Paths.get(o.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(sourcePath)) {
                sourceFile = sourcePath.toAbsolutePath().toString();
            } else {
                sourceFile = sourcePath.getFileName().toString();
            }
        } catch (URISyntaxException e) {
            sourceFile = "<unknown>";
        }

        return o.getClass().getName() + " from " + sourceFile;
    }
}
