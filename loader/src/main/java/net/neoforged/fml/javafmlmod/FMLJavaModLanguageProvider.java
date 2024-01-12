/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.neoforged.fml.Logging.SCAN;

public class FMLJavaModLanguageProvider implements IModLanguageProvider
{
    private static final Logger LOGGER = LogManager.getLogger();

    private static class FMLModTarget implements IModLanguageProvider.IModLanguageLoader {
        private static final Logger LOGGER = FMLJavaModLanguageProvider.LOGGER;
        private final String className;
        private final String modId;

        private FMLModTarget(String className, String modId)
        {
            this.className = className;
            this.modId = modId;
        }

        public String getModId()
        {
            return modId;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, ModuleLayer gameLayer)
        {
            return (T) new FMLModContainer(info, className, modFileScanResults, gameLayer);
        }
    }

    public static final Type MODANNOTATION = Type.getType("Lnet/neoforged/fml/common/Mod;");

    @Override
    public String name()
    {
        return "javafml";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return scanResult -> {
            final Map<String, FMLModTarget> modTargetMap = scanResult.getAnnotations().stream()
                    .filter(ad -> ad.annotationType().equals(MODANNOTATION))
                    .peek(ad -> LOGGER.debug(SCAN, "Found @Mod class {} with id {}", ad.clazz().getClassName(), ad.annotationData().get("value")))
                    .map(ad -> new FMLModTarget(ad.clazz().getClassName(), (String)ad.annotationData().get("value")))
                    .collect(Collectors.toMap(FMLModTarget::getModId, Function.identity(), (a,b)->a));
            scanResult.addLanguageLoader(modTargetMap);
        };
    }
}
