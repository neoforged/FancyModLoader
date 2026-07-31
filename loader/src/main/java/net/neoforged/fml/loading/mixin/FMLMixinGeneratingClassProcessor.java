/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import java.util.ArrayList;
import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.invoke.arg.ArgsClassGenerator;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.ISyntheticClassRegistry;

/**
 * Applies the parts of mixin transforms necessary for recomputing frames
 */
public class FMLMixinGeneratingClassProcessor implements ClassProcessor {
    private final FMLAuditTrail auditTrail;
    private final IMixinTransformer transformer;
    private final ISyntheticClassRegistry registry;
    private final FMLMixinService service;

    public FMLMixinGeneratingClassProcessor(FMLMixinService service) {
        this.service = service;
        this.auditTrail = service.getInternalAuditTrail();
        this.transformer = service.getMixinTransformer();
        this.registry = transformer.getExtensions().getSyntheticClassRegistry();
    }

    @Override
    public void link(LinkContext context) {
        this.service.setBytecodeProvider(new FMLClassBytecodeProvider(context.bytecodeProvider(), this.service));
    }

    @Override
    public ProcessorName name() {
        return ClassProcessorIds.MIXIN_FRAME_CONTEXT;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return Set.of();
    }

    @Override
    public Set<ProcessorName> runsBefore() {
        return Set.of(ClassProcessorIds.COMPUTING_FRAMES, ClassProcessorIds.MIXIN);
    }

    @Override
    public Set<String> generatesPackages() {
        return Set.of(ArgsClassGenerator.SYNTHETIC_PACKAGE);
    }

    static boolean generatesClass(ISyntheticClassRegistry registry, Type classType) {
        return registry.findSyntheticClass(classType.getClassName()) != null;
    }

    static boolean generateClass(IMixinTransformer transformer, Type classType, ClassNode classNode) {
        return transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), classType.getClassName(), classNode);
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        // This processor should process any class mixin generates
        // It will run twice; mixin's processors seem to be fine with this
        if (this.transformer.getExtensions().getSyntheticClassRegistry() == null) {
            return false;
        }

        return generatesClass(registry, context.type());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        var classType = context.type();
        var classNode = context.node();

        this.auditTrail.setConsumer(classType.getClassName(), context::audit);

        if (generatesClass(registry, classType)) {
            var innerClasses = classNode.innerClasses;
            var fields = classNode.fields;
            var methods = classNode.methods;
            var recordComponents = classNode.recordComponents;
            var permittedSubclasses = classNode.permittedSubclasses;
            var invisibleAnnotations = classNode.invisibleAnnotations;
            var invisibleTypeAnnotations = classNode.invisibleTypeAnnotations;
            var visibleAnnotations = classNode.visibleAnnotations;
            var visibleTypeAnnotations = classNode.visibleTypeAnnotations;
            var attrs = classNode.attrs;
            var nestMembers = classNode.nestMembers;

            var generated = generateClass(transformer, classType, classNode);
            if (generated) {
                // Clear out everything that the transformer will not expect to be there when it runs again
                // This should, in theory, leave basically just the superclass declaration and the like
                // Luckily, mixin class generators can be safely re-run (this is also done for the bytecode provider)
                //
                // If the context is not empty, 
                classNode.innerClasses = innerClasses;
                classNode.fields = fields;
                classNode.methods = methods;
                classNode.recordComponents = recordComponents;
                classNode.permittedSubclasses = permittedSubclasses;
                classNode.invisibleAnnotations = invisibleAnnotations;
                classNode.invisibleTypeAnnotations = invisibleTypeAnnotations;
                classNode.visibleAnnotations = visibleAnnotations;
                classNode.visibleTypeAnnotations = visibleTypeAnnotations;
                classNode.attrs = attrs;
                classNode.nestMembers = nestMembers;

                return ComputeFlags.SIMPLE_REWRITE;
            }
        }

        return ComputeFlags.NO_REWRITE;
    }
}
