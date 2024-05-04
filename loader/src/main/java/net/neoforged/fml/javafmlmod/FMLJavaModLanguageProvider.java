/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import java.lang.annotation.ElementType;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

public class FMLJavaModLanguageProvider implements IModLanguageLoader {
    public String name() {
        return "javafml";
    }

    @Override
    public ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer) {
        final var modClasses = modFileScanResults.getAnnotatedBy(Mod.class, ElementType.TYPE)
                .filter(data -> data.annotationData().get("value").equals(info.getModId()))
                .toList();
        if (modClasses.isEmpty()) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloading.javafml.missing_entrypoint").withAffectedMod(info));
        }
        return new FMLModContainer(info, modClasses
                .stream().filter(ad -> AutomaticEventSubscriber.getSides(ad.annotationData().get("dist")).contains(FMLLoader.getDist()))
                .map(ad -> ad.clazz().getClassName())
                .toList(), modFileScanResults, layer);
    }
}
