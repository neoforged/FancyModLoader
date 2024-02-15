/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.lowcodemod;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.ModFileScanData;

public class LowCodeModLanguageProvider implements IModLanguageProvider {
    private record LowCodeModTarget(String modId) implements IModLanguageProvider.IModLanguageLoader {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, ModuleLayer gameLayer) {
            return (T) new LowCodeModContainer(info, modFileScanResults, gameLayer);
        }
    }

    @Override
    public String name() {
        return "lowcodefml";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return scanResult -> {
            final Map<String, LowCodeModTarget> modTargetMap = scanResult.getIModInfoData().stream()
                    .flatMap(fi -> fi.getMods().stream())
                    .map(IModInfo::getModId)
                    .map(LowCodeModTarget::new)
                    .collect(Collectors.toMap(LowCodeModTarget::modId, Function.identity(), (a, b) -> a));
            scanResult.addLanguageLoader(modTargetMap);
        };
    }
}
