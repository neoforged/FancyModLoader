/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.coremod;

import static net.neoforged.fml.loading.LogMarkers.CORE;

import java.util.ServiceLoader;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide using the Java {@link ServiceLoader} mechanism as a {@link ClassProcessorProvider}.
 */
public interface CoreMod extends ClassProcessorProvider {
    /**
     * {@return the transformers provided by this coremod}
     */
    Iterable<? extends CoreModTransformer> getTransformers();

    @Override
    default void makeProcessors(ClassProcessorCollector collector) {
        final class WithLogger {
            private static final Logger LOGGER = LoggerFactory.getLogger(CoreMod.class);
        }

        // Try to identify the mod-file this is from
        var sourceFile = ServiceLoaderUtil.identifySourcePath(this);

        try {
            for (var transformer : getTransformers()) {
                WithLogger.LOGGER.debug(CORE, "Adding transformer {} from core-mod {} in {}", transformer.name(), this, sourceFile);
                collector.add(transformer.toProcessor());
            }
        } catch (Exception e) {
            // Throwing here would cause the game to immediately crash without a proper error screen,
            // since this method is called by ModLauncher directly. We also need to be able to attribute errors to
            // the actual mod causing them.
            ModLoader.addLoadingIssue(
                    ModLoadingIssue.error("fml.modloadingissue.coremod_error", this.getClass().getName(), sourceFile).withCause(e));
        }
    }
}
