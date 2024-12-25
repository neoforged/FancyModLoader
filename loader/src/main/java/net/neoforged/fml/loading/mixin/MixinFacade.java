/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.VirtualJar;
import cpw.mods.modlauncher.TransformingClassLoader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.injection.invoke.arg.ArgsClassGenerator;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;

/**
 * Encapsulates the code required to interact with Mixin.
 */
public final class MixinFacade {
    private static final Logger LOG = LoggerFactory.getLogger(MixinFacade.class);

    private final FMLMixinLaunchPlugin launchPlugin;
    private final FMLMixinService service;

    public MixinFacade() {
        if (FMLLoader.getDist() == null) {
            throw new IllegalStateException("The dist must be set before initializing Mixin");
        }

        System.setProperty("mixin.service", FMLMixinService.class.getName());
        System.setProperty("mixin.bootstrapService", FMLMixinServiceBootstrap.class.getName());

        MixinBootstrap.init();

        service = (FMLMixinService) MixinService.getService();
        this.launchPlugin = new FMLMixinLaunchPlugin(service);
    }

    public FMLMixinLaunchPlugin getLaunchPlugin() {
        return launchPlugin;
    }

    public void finishInitialization(LoadingModList loadingModList, TransformingClassLoader classLoader) {
        if (Thread.currentThread().getContextClassLoader() != classLoader) {
            throw new IllegalStateException("The class loader must be the context classloader in order to find the Mixin configurations.");
        }
        addMixins(loadingModList);

        // We must transition to DEFAULT phase for normal Mixins to be applied at all
        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);

        service.setBytecodeProvider(new FMLClassBytecodeProvider(classLoader, this.launchPlugin));
        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
    }

    private void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            var m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, phase);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addMixins(LoadingModList loadingModList) {
        var modFiles = loadingModList.getModFiles();
        var mixinConfigFiles = modFiles
                .stream()
                .map(ModFileInfo::getFile)
                .flatMap(mf -> mf.getMixinConfigs().stream().map(file -> Map.entry(mf, file)))
                .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        LOG.info("Adding {} mixin configuration files", mixinConfigFiles.size());
        LOG.debug("Adding mixin configuration files: {}", mixinConfigFiles.keySet());
        for (var entry : mixinConfigFiles.entrySet()) {
            if (entry.getValue().size() > 1) {
                LOG.error("Mixin config filename {} is used by more than one mod: {}", entry.getKey(), entry.getValue());
            }
        }
        Mixins.addConfigurations(mixinConfigFiles.keySet().toArray(String[]::new));

        decorateMixinConfigsWithModIds(modFiles);
    }

    private void decorateMixinConfigsWithModIds(List<ModFileInfo> modFiles) {
        // Decorate mixin configurations with their source mod id
        var configMap = Mixins.getConfigs().stream()
                .collect(Collectors.toMap(Config::getName, Config::getConfig));
        for (var modFile : modFiles) {
            var modId = modFile.getFile().getPrimaryModId();
            for (var configPath : modFile.getFile().getMixinConfigs()) {
                var config = configMap.get(configPath);
                if (config == null) {
                    LOG.warn("Mixin config file {} was not registered!", configPath);
                } else {
                    config.decorate(FabricUtil.KEY_MOD_ID, modId);
                }
            }
        }
    }

    public SecureJar createGeneratedCodeContainer() {
        Path codeSource;
        try {
            codeSource = Path.of(Mixins.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return new VirtualJar("mixin_synthetic", codeSource, ArgsClassGenerator.SYNTHETIC_PACKAGE);
    }
}
