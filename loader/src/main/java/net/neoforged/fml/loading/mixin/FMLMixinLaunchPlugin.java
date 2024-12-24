/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import java.util.function.Consumer;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.Phases;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.ISyntheticClassRegistry;

public class FMLMixinLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "fml-mixin";

    private final FMLAuditTrail auditTrail;
    private final FMLClassTracker classTracker;
    private final IMixinTransformer transformer;
    private final ISyntheticClassRegistry registry;

    public FMLMixinLaunchPlugin(FMLMixinService service) {
        this.auditTrail = service.getAuditTrail();
        this.classTracker = service.getClassTracker();
        this.transformer = service.getMixinTransformer();
        this.registry = transformer.getExtensions().getSyntheticClassRegistry();
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
            return Phases.NONE;
        }

        String name = classType.getClassName();
        if (classTracker.isInvalidClass(name)) {
            throw new NoClassDefFoundError(String.format("%s is invalid", name));
        }

        if (!isEmpty) {
            return Phases.AFTER_ONLY;
        }

        if (this.registry == null) {
            return Phases.NONE;
        }

        return this.generatesClass(classType) ? Phases.AFTER_ONLY : Phases.NONE;
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        try {
            if (phase == Phase.BEFORE) {
                return false;
            }

            // Don't transform when the reason is mixin (side-loading in progress)
            if (FMLMixinLaunchPlugin.NAME.equals(reason)) {
                return false;
            }

            if (this.generatesClass(classType)) {
                return this.generateClass(classType, classNode);
            }

            MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
            if (ITransformerActivity.COMPUTING_FRAMES_REASON.equals(reason)) {
                return this.transformer.computeFramesForClass(environment, classType.getClassName(), classNode);
            }

            return this.transformer.transformClass(environment, classType.getClassName(), classNode);
        } finally {
            // Only track the classload if the reason is actually classloading
            if (ITransformerActivity.CLASSLOADING_REASON.equals(reason)) {
                classTracker.addLoadedClass(classType.getClassName());
            }
        }
    }

    @Override
    public void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
        this.auditTrail.setConsumer(className, auditDataAcceptor);
    }

    boolean generatesClass(Type classType) {
        return this.registry.findSyntheticClass(classType.getClassName()) != null;
    }

    boolean generateClass(Type classType, ClassNode classNode) {
        return this.transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), classType.getClassName(), classNode);
    }
}
