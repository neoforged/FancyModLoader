/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.CoreModTransformationContextImpl;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.mixin.FMLMixinClassProcessor;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

@ApiStatus.Internal
public class CoreModsTransformerProvider implements ClassProcessorProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void makeProcessors(ClassProcessorCollector collector, ILaunchContext launchContext) {
        LOGGER.debug(LOADING, "Loading coremod transformers");

        // Find all Java core mods
        for (var coreMod : ServiceLoaderUtil.loadServices(launchContext, ICoreMod.class)) {
            // Try to identify the mod-file this is from
            var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, coreMod);

            try {
                for (var transformer : coreMod.getTransformers()) {
                    LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), coreMod, sourceFile);
                    collector.add(makeTransformer(transformer));
                }
                collector.add(new ClassProcessor() {
                    // For ordering purposes only; allows making transformers that run before/after all "default" coremods
                    @Override
                    public ProcessorName name() {
                        return ITransformer.COREMODS_GROUP;
                    }

                    @Override
                    public boolean handlesClass(SelectionContext context) {
                        return false;
                    }

                    @Override
                    public Set<ProcessorName> runsAfter() {
                        return Set.of(COMPUTING_FRAMES, FMLMixinClassProcessor.NAME);
                    }

                    @Override
                    public ComputeFlags processClass(TransformationContext context) {
                        return ComputeFlags.NO_REWRITE;
                    }
                });
            } catch (Exception e) {
                // Throwing here would cause the game to immediately crash without a proper error screen,
                // since this method is called by ModLauncher directly.
                ModLoader.addLoadingIssue(
                        ModLoadingIssue.error("fml.modloadingissue.coremod_error", coreMod.getClass().getName(), sourceFile).withCause(e));
            }
        }
    }

    @VisibleForTesting
    public static ClassProcessor makeTransformer(ITransformer transformer) {
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
            public ComputeFlags processClass(TransformationContext processContext) {
                var targets = targetsByClassName.get(processContext.type().getClassName());
                if (targets.isEmpty()) {
                    return ComputeFlags.NO_REWRITE;
                }
                boolean transformed = false;
                switch (transformer) {
                    case ITransformer.ClassTransformer classTransformer -> {
                        var context = new CoreModTransformationContextImpl(processContext, processContext.node());
                        classTransformer.transform(processContext.node(), context);
                        transformed = true;
                    }
                    case ITransformer.MethodTransformer methodTransformer -> {
                        var methodNameDescs = new HashSet<String>();
                        for (var target : targets) {
                            var methodTarget = (ITransformer.Target.MethodTarget) target;
                            methodNameDescs.add(methodTarget.methodName() + methodTarget.methodDescriptor());
                        }
                        for (var method : processContext.node().methods) {
                            if (methodNameDescs.contains(method.name + method.desc)) {
                                var context = new CoreModTransformationContextImpl(processContext, method);
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
                        for (var field : processContext.node().fields) {
                            if (fieldNames.contains(field.name)) {
                                var context = new CoreModTransformationContextImpl(processContext, field);
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
