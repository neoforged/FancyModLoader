/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.CoremodTransformationContextImpl;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.ArrayList;
import java.util.Collection;
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
    public Collection<ClassProcessor> makeTransformers(ILaunchContext launchContext) {
        LOGGER.debug(LOADING, "Loading coremod transformers");

        var result = new ArrayList<ClassProcessor>();

        // Find all Java core mods
        for (var coreMod : ServiceLoaderUtil.loadServices(launchContext, ICoreMod.class)) {
            // Try to identify the mod-file this is from
            var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, coreMod);

            try {
                for (var transformer : coreMod.getTransformers()) {
                    LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), coreMod, sourceFile);
                    result.add(makeTransformer(transformer));
                }
                result.add(new ClassProcessor() {
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
                });
            } catch (Exception e) {
                // Throwing here would cause the game to immediately crash without a proper error screen,
                // since this method is called by ModLauncher directly.
                ModLoader.addLoadingIssue(
                        ModLoadingIssue.error("fml.modloadingissue.coremod_error", coreMod.getClass().getName(), sourceFile).withCause(e));
            }
        }

        return result;
    }

    @VisibleForTesting
    public static <T> ClassProcessor makeTransformer(ITransformer<T> transformer) {
        Map<String, List<ITransformer.Target<T>>> targetsByClassName = transformer.targets().stream()
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

            @SuppressWarnings("unchecked")
            @Override
            public boolean processClass(TransformationContext processContext) {
                var targets = targetsByClassName.get(processContext.type().getClassName());
                if (targets.isEmpty()) {
                    return false;
                }
                boolean transformed = false;
                switch (transformer.getTargetType()) {
                    case CLASS -> {
                        var context = new CoremodTransformationContextImpl(processContext, processContext.node());
                        transformer.transform((T) processContext.node(), context);
                        transformed = true;
                    }
                    case METHOD -> {
                        var methodNameDescs = new HashSet<String>();
                        for (var target : targets) {
                            methodNameDescs.add(target.elementName() + target.elementDescriptor());
                        }
                        for (var method : processContext.node().methods) {
                            if (methodNameDescs.contains(method.name + method.desc)) {
                                var context = new CoremodTransformationContextImpl(processContext, method);
                                transformer.transform((T) method, context);
                                transformed = true;
                            }
                        }
                    }
                    case FIELD -> {
                        var fieldNames = new HashSet<String>();
                        for (var target : targets) {
                            fieldNames.add(target.elementName());
                        }
                        for (var field : processContext.node().fields) {
                            if (fieldNames.contains(field.name)) {
                                var context = new CoremodTransformationContextImpl(processContext, field);
                                transformer.transform((T) field, context);
                                transformed = true;
                            }
                        }
                    }
                }
                return transformed;
            }
        };
    }
}
