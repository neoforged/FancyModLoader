/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import java.util.function.Consumer;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.Phases;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.MixinService;

public class FMLMixinLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "fml-mixin";

    private MixinFacade facade;
    private final FMLMixinService service;

    public FMLMixinLaunchPlugin() {
        System.setProperty("mixin.service", FMLMixinService.class.getName());
        System.setProperty("mixin.bootstrapService", FMLMixinServiceBootstrap.class.getName());

        this.service = (FMLMixinService) MixinService.getService();
    }

    public synchronized void setup() {
        if (this.facade == null) {
            this.facade = new MixinFacade(this);
        }
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

        if (!processesClass(classType)) {
            return Phases.NONE; // If there is no chance of the class being processed, we do not bother.
        }

        // Throw if the class was previously determined to be invalid
        String name = classType.getClassName();
        if (this.service.getClassTracker().isInvalidClass(name)) {
            throw new NoClassDefFoundError(String.format("%s is invalid", name));
        }

        if (!isEmpty) {
            return Phases.AFTER_ONLY;
        }

        if (this.service.getMixinTransformer().getExtensions().getSyntheticClassRegistry() == null) {
            return Phases.NONE;
        }

        return this.generatesClass(classType) ? Phases.AFTER_ONLY : Phases.NONE;
    }

    private boolean processesClass(Type classType) {
        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
        return this.service.getMixinTransformer().couldTransformClass(environment, classType.getClassName());
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
                return this.service.getMixinTransformer().computeFramesForClass(environment, classType.getClassName(), classNode);
            }

            return this.service.getMixinTransformer().transformClass(environment, classType.getClassName(), classNode);
        } finally {
            // Only track the classload if the reason is actually classloading
            if (ITransformerActivity.CLASSLOADING_REASON.equals(reason)) {
                this.service.getClassTracker().addLoadedClass(classType.getClassName());
            }
        }
    }

    @Override
    public void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
        this.service.getAuditTrail().setConsumer(className, auditDataAcceptor);
    }

    boolean generatesClass(Type classType) {
        return this.service.getMixinTransformer().getExtensions().getSyntheticClassRegistry().findSyntheticClass(classType.getClassName()) != null;
    }

    boolean generateClass(Type classType, ClassNode classNode) {
        return this.service.getMixinTransformer().generateClass(MixinEnvironment.getCurrentEnvironment(), classType.getClassName(), classNode);
    }
}
