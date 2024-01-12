/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.mclanguageprovider;

import net.neoforged.neoforgespi.language.ILifecycleEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.neoforged.fml.Logging.LOADING;

public class MinecraftModLanguageProvider implements IModLanguageProvider {
    private static final Logger LOGGER = LogManager.getLogger();
    @Override
    public String name() {
        return "minecraft";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return (sd)->sd.addLanguageLoader(Map.of("minecraft", new MinecraftModTarget()));
    }

    @Override
    public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(final Supplier<R> consumeEvent) {

    }

    public static class MinecraftModTarget implements IModLanguageLoader {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, final ModuleLayer gameLayer) {
            try {
                var module = gameLayer.findModule("minecraft").orElseThrow();
                final Class<?> mcModClass = Class.forName(getClass().getModule(), "net.neoforged.fml.mclanguageprovider.MinecraftModContainer");
                return (T)mcModClass.getConstructor(IModInfo.class).newInstance(info);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                LOGGER.fatal(LOADING,"Unable to load MinecraftModContainer, wut?", e);
                throw new RuntimeException(e);
            }
        }
    }
}
