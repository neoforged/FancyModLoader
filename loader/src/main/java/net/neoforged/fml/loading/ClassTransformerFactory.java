/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for creating the {@link ClassTransformer} based on the available transformers, core mods
 * and launch plugins.
 */
final class ClassTransformerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassTransformerFactory.class);

    private ClassTransformerFactory() {}

    public static ClassTransformer create(ILaunchContext launchContext,
            LaunchPluginHandler launchPluginHandler) {
        // Discover third party transformation services
        var transformationServices = ServiceLoaderUtil.loadServices(
                launchContext,
                ITransformationService.class,
                List.of(),
                ClassTransformerFactory::isValidTransformationService);

        var transformStore = new TransformStore();

        for (var service : transformationServices) {
            try {
                var transformers = service.transformers();
                for (var transform : transformers) {
                    transformStore.addTransformer(transform, service.name());
                }
                LOGGER.debug("Initialized transformers for transformation service {}", service.name());
            } catch (Exception e) {
                var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, service);
                ModLoader.addLoadingIssue(
                        ModLoadingIssue.error(
                                "fml.modloadingissue.coremod_error",
                                service.getClass().getName(),
                                sourceFile).withCause(e));
            }
        }

        for (var xform : getCoreModTransformers(launchContext)) {
            transformStore.addTransformer(xform, "");
        }

        return new ClassTransformer(transformStore, launchPluginHandler);
    }

    private static List<? extends ITransformer<?>> getCoreModTransformers(ILaunchContext launchContext) {
        LOGGER.debug(LOADING, "Loading coremod transformers");

        var result = new ArrayList<ITransformer<?>>();

        // Find all Java core mods
        for (var coreMod : ServiceLoaderUtil.loadServices(launchContext, ICoreMod.class)) {
            // Try to identify the mod-file this is from
            var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, coreMod);

            try {
                for (var transformer : coreMod.getTransformers()) {
                    LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), coreMod, sourceFile);
                    result.add(transformer);
                }
            } catch (Exception e) {
                // Throwing here would cause the game to immediately crash without a proper error screen,
                // since this method is called by ModLauncher directly.
                ModLoader.addLoadingIssue(
                        ModLoadingIssue.error("fml.modloadingissue.coremod_error", coreMod.getClass().getName(), sourceFile).withCause(e));
            }
        }

        return result;
    }

    private static <T extends ITransformationService> boolean isValidTransformationService(Class<T> serviceClass) {
        // Blacklist all Mixin services, since we implement all of them ourselves
        return !MixinFacade.isMixinServiceClass(serviceClass);
    }
}
