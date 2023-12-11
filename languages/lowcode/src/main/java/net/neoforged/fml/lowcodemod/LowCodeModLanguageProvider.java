/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.lowcodemod;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingStage;
import net.neoforged.neoforgespi.language.ILifecycleEvent;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.neoforged.fml.Logging.LOADING;

public class LowCodeModLanguageProvider implements IModLanguageProvider
{
    private record LowCodeModTarget(String modId) implements IModLanguageProvider.IModLanguageLoader
    {
        private static final Logger LOGGER = LogManager.getLogger();

        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, ModuleLayer gameLayer)
        {
            // This language class is loaded in the system level classloader - before the game even starts
            // So we must treat container construction as an arms length operation, and load the container
            // in the classloader of the game - the context classloader is appropriate here.
            try {
                final Class<?> fmlContainer = Class.forName("net.neoforged.fml.lowcodemod.LowCodeModContainer", true, Thread.currentThread().getContextClassLoader());
                LOGGER.debug(LOADING, "Loading LowCodeModContainer from classloader {} - got {}", Thread.currentThread().getContextClassLoader(), fmlContainer.getClassLoader());
                final Constructor<?> constructor = fmlContainer.getConstructor(IModInfo.class, ModFileScanData.class, ModuleLayer.class);
                return (T) constructor.newInstance(info, modFileScanResults, gameLayer);
            } catch (InvocationTargetException e) {
                LOGGER.fatal(LOADING, "Failed to build mod", e);
                if (e.getTargetException() instanceof ModLoadingException mle) {
                    throw mle;
                } else {
                    throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
                }
            } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                LOGGER.fatal(LOADING, "Unable to load LowCodeModContainer, wut?", e);
                final Class<RuntimeException> mle = (Class<RuntimeException>) LamdbaExceptionUtils.uncheck(() -> Class.forName("net.neoforged.fml.ModLoadingException", true, Thread.currentThread().getContextClassLoader()));
                final Class<ModLoadingStage> mls = (Class<ModLoadingStage>) LamdbaExceptionUtils.uncheck(() -> Class.forName("net.neoforged.fml.ModLoadingStage", true, Thread.currentThread().getContextClassLoader()));
                throw LamdbaExceptionUtils.uncheck(() -> LamdbaExceptionUtils.uncheck(() -> mle.getConstructor(IModInfo.class, mls, String.class, Throwable.class)).newInstance(info, Enum.valueOf(mls, "CONSTRUCT"), "fml.modloading.failedtoloadmodclass", e));
            }
        }
    }

    @Override
    public String name()
    {
        return "lowcodefml";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor()
    {
        return scanResult ->
        {
            final Map<String, LowCodeModTarget> modTargetMap = scanResult.getIModInfoData().stream()
                    .flatMap(fi->fi.getMods().stream())
                    .map(IModInfo::getModId)
                    .map(LowCodeModTarget::new)
                    .collect(Collectors.toMap(LowCodeModTarget::modId, Function.identity(), (a, b)->a));
            scanResult.addLanguageLoader(modTargetMap);
        };
    }

    @Override
    public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(final Supplier<R> consumeEvent)
    {
    }
}
