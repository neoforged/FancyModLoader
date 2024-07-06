/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ModuleAccessDeclarations {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleAccessDeclarations.class);

    private ModuleAccessDeclarations() {}

    public static void apply(Instrumentation instrumentation, ModuleLayer layer) {
        // TODO: This should be read from:
        // 1) the manifests of all participating modules
        // 2) config file for modpacks
        var additionalAddOpens = List.of(
                new Declaration("org.lwjgl", "org.lwjgl.system", "minecraft"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer", "mixinextras.neoforge"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer.ext", "mixinextras.neoforge"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.injection.struct", "mixinextras.neoforge"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer.ext", "MixinSquared"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer", "MixinSquared"),
                new Declaration("com.google.gson", "com.google.gson.stream", "minecraft"));
        var additionalAddExports = List.of(
                new Declaration("com.google.gson", "com.google.gson.internal", "prickle"),
                new Declaration("com.google.gson", "com.google.gson.internal", "supermartijn642configlib"),
                new Declaration("com.google.gson", "com.google.gson.internal", "moonlight"),
                new Declaration("com.google.gson", "com.google.gson.internal", "kubejs"),
                new Declaration("com.google.gson", "com.google.gson.internal", "structurize"),
                new Declaration("com.google.gson", "com.google.gson.internal", "rhino"),
                new Declaration("com.google.gson", "com.google.gson.internal", "minecraft"),
                new Declaration("com.google.gson", "com.google.gson.internal", "minecolonies"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.injection.invoke", "immersiveengineering"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer.ext.extensions", "mixinextras.neoforge"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.injection.modify", "mixinextras.neoforge"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer.meta", "modernfix"),
                new Declaration("org.spongepowered.mixin", "org.spongepowered.asm.mixin.transformer", "MixinSquared"));

        var addOpensBySource = additionalAddOpens.stream().collect(Collectors.groupingBy(Declaration::module));
        var addExportBySource = additionalAddExports.stream().collect(Collectors.groupingBy(Declaration::module));
        var sourceModules = new HashSet<>(addOpensBySource.keySet());
        sourceModules.addAll(addExportBySource.keySet());

        for (var sourceModuleName : sourceModules) {
            var sourceModule = layer.findModule(sourceModuleName).orElse(null);
            if (sourceModule == null) {
                LOGGER.debug("Skipping module access declarations for {} since it does not exist.", sourceModuleName);
                continue;
            }

            var extraOpens = groupAndResolveDeclarations(
                    layer,
                    addOpensBySource.getOrDefault(sourceModuleName, List.of()));
            var extraExports = groupAndResolveDeclarations(
                    layer,
                    addExportBySource.getOrDefault(sourceModuleName, List.of()));

            if (!extraOpens.isEmpty() || !extraExports.isEmpty()) {
                if (!extraOpens.isEmpty()) {
                    LOGGER.info("Adding opens to {}: {}", sourceModule.getName(), extraOpens);
                }
                if (!extraExports.isEmpty()) {
                    LOGGER.info("Adding exports to {}: {}", sourceModule.getName(), extraExports);
                }
                instrumentation.redefineModule(
                        sourceModule,
                        Set.of(),
                        extraExports,
                        extraOpens,
                        Set.of(),
                        Map.of());
            }
        }
    }

    private static Map<String, Set<Module>> groupAndResolveDeclarations(ModuleLayer layer, List<Declaration> addOpens) {
        var extraOpens = new HashMap<String, Set<Module>>();
        for (var pkgEntry : addOpens.stream().collect(Collectors.groupingBy(Declaration::packageName)).entrySet()) {
            var packageName = pkgEntry.getKey();
            var targetModules = pkgEntry.getValue().stream()
                    .map(decl -> layer.findModule(decl.target()).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!targetModules.isEmpty()) {
                extraOpens.put(packageName, targetModules);
            }
        }
        return extraOpens;
    }

    record Declaration(String module, String packageName, String target) {}
}
