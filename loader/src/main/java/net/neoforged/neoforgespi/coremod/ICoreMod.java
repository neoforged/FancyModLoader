/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.coremod;

import static net.neoforged.fml.loading.LogMarkers.CORE;

import cpw.mods.modlauncher.api.ITransformer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide using the Java {@link java.util.ServiceLoader} mechanism as a {@link ClassProcessorProvider}.
 */
public interface ICoreMod extends ClassProcessorProvider {
    /**
     * {@return the transformers provided by this coremod}
     */
    Iterable<? extends ITransformer> getTransformers();

    @Override
    default void makeProcessors(ClassProcessorCollector collector) {
        final class WithLogger {
            private static final Logger LOGGER = LoggerFactory.getLogger(ICoreMod.class);
        }

        // Try to identify the mod-file this is from
        var sourceFile = ServiceLoaderUtil.identifySourcePath(this);

        try {
            for (var transformer : this.getTransformers()) {
                WithLogger.LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), this, sourceFile);
                collector.add(makeTransformer(transformer));
            }
        } catch (Exception e) {
            // Throwing here would cause the game to immediately crash without a proper error screen,
            // since this method is called by ModLauncher directly.
            ModLoader.addLoadingIssue(
                    ModLoadingIssue.error("fml.modloadingissue.coremod_error", this.getClass().getName(), sourceFile).withCause(e));
        }
    }

    private static ClassProcessor makeTransformer(ITransformer transformer) {
        Map<String, List<ITransformer.Target>> targetsByClassName = transformer.targets().stream()
                .collect(Collectors.groupingBy(ITransformer.Target::className));
        Set<ProcessorName> before = new HashSet<>(transformer.runsBefore());
        Set<ProcessorName> after = new HashSet<>(transformer.runsAfter());
        // coremod transformers always imply COMPUTE_FRAMES and thus must always run after it.
        after.add(ClassProcessor.COMPUTING_FRAMES);

        return new ClassProcessor() {
            @Override
            public ProcessorName name() {
                return transformer.name();
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                return targetsByClassName.containsKey(context.type().getClassName());
            }

            @Override
            public Set<ProcessorName> runsBefore() {
                return before;
            }

            @Override
            public Set<ProcessorName> runsAfter() {
                return after;
            }

            @Override
            public ComputeFlags processClass(TransformationContext context) {
                var targets = targetsByClassName.get(context.type().getClassName());
                if (targets.isEmpty()) {
                    return ComputeFlags.NO_REWRITE;
                }
                boolean transformed = false;
                switch (transformer) {
                    case ITransformer.ClassTransformer classTransformer -> {
                        classTransformer.transform(context.node(), context);
                        transformed = true;
                    }
                    case ITransformer.MethodTransformer methodTransformer -> {
                        var methodNameDescs = new HashSet<String>();
                        for (var target : targets) {
                            var methodTarget = (ITransformer.Target.MethodTarget) target;
                            methodNameDescs.add(methodTarget.methodName() + methodTarget.methodDescriptor());
                        }
                        for (var method : context.node().methods) {
                            if (methodNameDescs.contains(method.name + method.desc)) {
                                methodTransformer.transform(method, context);
                                transformed = true;
                            }
                        }
                    }
                    case ITransformer.FieldTransformer fieldTransformer -> {
                        var fieldNames = new HashSet<String>();
                        for (var target : targets) {
                            var fieldTarget = (ITransformer.Target.FieldTarget) target;
                            fieldNames.add(fieldTarget.fieldName());
                        }
                        for (var field : context.node().fields) {
                            if (fieldNames.contains(field.name)) {
                                fieldTransformer.transform(field, context);
                                transformed = true;
                            }
                        }
                    }
                }
                return transformed ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
            }
        };
    }
}
