/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.util;

import cpw.mods.niofs.union.UnionFileSystem;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.jarjar.nio.pathfs.PathFileSystem;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IOrderedProvider;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.function.Predicate;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class ServiceLoaderUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceLoaderUtil.class);

    private ServiceLoaderUtil() {
    }

    public static <T> List<T> loadServices(ILaunchContext context, Class<T> serviceClass) {
        return loadServices(context, serviceClass, List.of());
    }

    public static <T> List<T> loadServices(ILaunchContext context, Class<T> serviceClass, Predicate<Class<? extends T>> filter) {
        return loadServices(context, serviceClass, List.of(), filter);
    }

    public static <T> List<T> loadServices(ILaunchContext context, Class<T> serviceClass, Collection<T> additionalServices) {
        return loadServices(context, serviceClass, additionalServices, ignored -> true);
    }

    /**
     * @param serviceClass If the service class implements {@link IOrderedProvider}, the services will automatically be sorted.
     */
    public static <T> List<T> loadServices(ILaunchContext context,
                                           Class<T> serviceClass,
                                           Collection<T> additionalServices,
                                           Predicate<Class<? extends T>> filter) {
        var serviceLoaderServices = context.loadServices(serviceClass)
                .filter(p -> {
                    if (!filter.test(p.type())) {
                        LOGGER.debug("Filtering out service provider {} for service class {}", p.type(), serviceClass);
                        return false;
                    }
                    return true;
                })
                .map(p -> {
                    try {
                        return p.get();
                    } catch (ServiceConfigurationError sce) {
                        LOGGER.error("Failed to load implementation for {}", serviceClass, sce);
                        return null;
                    }
                }).filter(Objects::nonNull);

        var servicesStream = Stream.concat(additionalServices.stream(), serviceLoaderServices).distinct();

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
                    LOGGER.debug(LogMarkers.CORE, "\t{}[built-in] {}", priorityPrefix, identifyService(context, service));
                } else {
                    LOGGER.debug(LogMarkers.CORE, "\t{}{}", priorityPrefix, identifyService(context, service));
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
        try {
            return unwrapPath(context, Paths.get(codeLocation.toURI()));
        } catch (URISyntaxException e) {
            return codeLocation.toString();
        }
    }

    /**
     * Tries to unwrap the given path if it is from a nested file-system such as JIJ or UnionFS,
     * while maintaining context in the return (such as "&lt;nested path>" from "&lt;outer jar>").
     */
    private static String unwrapPath(ILaunchContext context, Path path) {
        if (path.getFileSystem() instanceof PathFileSystem pathFileSystem) {
            return unwrapPath(context, pathFileSystem.getTarget());
        } else if (path.getFileSystem() instanceof UnionFileSystem unionFileSystem) {
            if (path.equals(unionFileSystem.getRoot())) {
                return unwrapPath(context, unionFileSystem.getPrimaryPath());
            }
            return unwrapPath(context, unionFileSystem.getPrimaryPath()) + " > " + relativizePath(context, path);
        }
        return relativizePath(context, path);
    }

    private static String relativizePath(ILaunchContext context, Path path) {
        var gameDir = context.gameDirectory();

        String resultPath;

        if (gameDir != null && path.startsWith(gameDir)) {
            resultPath = gameDir.relativize(path).toString();
        } else if (Files.isDirectory(path)) {
            resultPath = path.toAbsolutePath().toString();
        } else {
            resultPath = path.getFileName().toString();
        }

        // Unify separators to ensure it is easier to test
        return resultPath.replace('\\', '/');
    }
}
