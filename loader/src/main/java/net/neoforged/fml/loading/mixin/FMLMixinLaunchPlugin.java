/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.launch.Phases;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

public class FMLMixinLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "fml-mixin";
    private static final Logger LOGGER = LogUtils.getLogger();

    private MixinFacade facade;

    public FMLMixinLaunchPlugin() {
        System.setProperty("mixin.service", FMLMixinService.class.getName());
        System.setProperty("mixin.bootstrapService", FMLMixinServiceBootstrap.class.getName());
    }

    public synchronized void setup(List<String> extraMixinConfigs) {
        if (this.facade == null) {
            this.facade = new MixinFacade(this);
        }

        Map<String, String> configModIds = new HashMap<>();
        var modList = LoadingModList.get();

        extraMixinConfigs.forEach(Mixins::addConfiguration);

        modList.getModFiles().stream()
                .map(ModFileInfo::getFile)
                .forEach(file -> {
                    final String modId = file.getModInfos().getFirst().getModId();
                    for (ModFileParser.MixinConfig potential : file.getMixinConfigs()) {
                        var existingModId = configModIds.putIfAbsent(potential.config(), modId);
                        if (existingModId != null && !existingModId.equals(modId)) {
                            LOGGER.error("Mixin config {} is registered by multiple mods: {} and {}", potential.config(), existingModId, modId);
                            throw new IllegalStateException("Mixin config " + potential.config() + " is registered by multiple mods: " + existingModId + " and " + modId);
                        }
                        if (potential.requiredMods().stream().allMatch(id -> modList.getModFileById(id) != null)) {
                            Mixins.addConfiguration(potential.config());
                        } else {
                            LOGGER.debug("Mixin config {} for mod {} not applied as required mods are missing", potential.config(), modId);
                        }
                    }
                });

        final var configMap = Mixins.getConfigs().stream().collect(
                Collectors.toMap(Config::getName, Config::getConfig));
        configModIds.forEach((fileName, modId) -> {
            if (modId == null) return;

            final var config = configMap.get(fileName);
            if (config == null) {
                LOGGER.error("Config file {} was not registered!", fileName);
            } else {
                config.decorate(FabricUtil.KEY_MOD_ID, modId);
            }
        });
    }

    public synchronized MixinFacade getFacade() {
        if (this.facade == null) {
            throw new IllegalStateException("MixinFacade has not been set up yet");
        }
        return this.facade;
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
        getFacade().finishInitialization(transformerLoader);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        throw new IllegalStateException("Outdated ModLauncher");
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        if (NAME.equals(reason)) {
            return Phases.NONE; // We're recursively loading classes to look up inheritance hierarchies. Avoid infinite recursion.
        }

        // Throw if the class was previously determined to be invalid
        String name = classType.getClassName();
        if (this.facade.getService().getClassTracker().isInvalidClass(name)) {
            throw new NoClassDefFoundError(String.format("%s is invalid", name));
        }

        if (!isEmpty) {
            if (processesClass(classType)) {
                return Phases.AFTER_ONLY;
            }
            // If there is no chance of the class being processed, we do not bother.
        }

        if (this.facade.getService().getMixinTransformer().getExtensions().getSyntheticClassRegistry() == null) {
            return Phases.NONE;
        }

        return this.generatesClass(classType) ? Phases.AFTER_ONLY : Phases.NONE;
    }

    private boolean processesClass(Type classType) {
        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
        return this.facade.getService().getMixinTransformer().couldTransformClass(environment, classType.getClassName());
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        try {
            if (phase == Phase.BEFORE) {
                return false;
            }

            // Don't transform when the reason is mixin (side-loading in progress)
            // NOTE: we opt-out in handlesClass too
            if (NAME.equals(reason)) {
                return false;
            }

            if (this.generatesClass(classType)) {
                return this.generateClass(classType, classNode);
            }

            MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
            if (ITransformerActivity.COMPUTING_FRAMES_REASON.equals(reason)) {
                return this.facade.getService().getMixinTransformer().computeFramesForClass(environment, classType.getClassName(), classNode);
            }

            return this.facade.getService().getMixinTransformer().transformClass(environment, classType.getClassName(), classNode);
        } finally {
            // Only track the classload if the reason is actually classloading
            if (ITransformerActivity.CLASSLOADING_REASON.equals(reason)) {
                this.facade.getService().getClassTracker().addLoadedClass(classType.getClassName());
            }
        }
    }

    @Override
    public void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
        this.facade.getService().getAuditTrail().setConsumer(className, auditDataAcceptor);
    }

    boolean generatesClass(Type classType) {
        return this.facade.getService().getMixinTransformer().getExtensions().getSyntheticClassRegistry().findSyntheticClass(classType.getClassName()) != null;
    }

    boolean generateClass(Type classType, ClassNode classNode) {
        return this.facade.getService().getMixinTransformer().generateClass(MixinEnvironment.getCurrentEnvironment(), classType.getClassName(), classNode);
    }
}
