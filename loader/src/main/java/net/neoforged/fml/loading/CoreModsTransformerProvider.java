package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.TransformationContext;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.transformation.IClassProcessor;
import net.neoforged.neoforgespi.transformation.IClassProcessorProvider;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.neoforged.fml.loading.LogMarkers.CORE;
import static net.neoforged.fml.loading.LogMarkers.LOADING;

public class CoreModsTransformerProvider implements IClassProcessorProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Override
    public List<IClassProcessor> makeTransformers(ILaunchContext launchContext) {
        LOGGER.debug(LOADING, "Loading coremod transformers");

        var result = new ArrayList<IClassProcessor>();

        // Find all Java core mods
        for (var coreMod : ServiceLoaderUtil.loadServices(launchContext, ICoreMod.class)) {
            // Try to identify the mod-file this is from
            var sourceFile = ServiceLoaderUtil.identifySourcePath(launchContext, coreMod);

            try {
                for (var transformer : coreMod.getTransformers()) {
                    LOGGER.debug(CORE, "Adding {} transformer from core-mod {} in {}", transformer.targets(), coreMod, sourceFile);
                    result.add(makeTransformer(transformer));
                }
            } catch (Exception e) {
                // Throwing here would cause the game to immediately crash without a proper error screen,
                // since this method is called by ModLauncher directly.
                ModLoader.addLoadingIssue(
                        ModLoadingIssue.error("fml.modloadingissue.coremod_error", coreMod.getClass().getName(), sourceFile).withCause(e));
            }
        }
        
        return result;
    }

    private <T> IClassProcessor makeTransformer(ITransformer<T> transformer) {
        Map<String, List<ITransformer.Target<T>>> targetsByClassName = transformer.targets().stream()
                .collect(Collectors.groupingBy(ITransformer.Target::className));
        Set<String> before = new HashSet<>(transformer.runsBefore());
        Set<String> after = new HashSet<>(transformer.runsAfter());
        // coremod transformers always imply COMPUTE_FRAMES and thus must always run after it.
        after.add(IClassProcessor.COMPUTING_FRAMES);
        
        return new IClassProcessor() {
            @Override
            public String name() {
                return transformer.name();
            }

            @Override
            public boolean handlesClass(Type classType, boolean isEmpty) {
                return targetsByClassName.containsKey(classType.getClassName());
            }

            @Override
            public Set<String> runsBefore() {
                return before;
            }

            @Override
            public Set<String> runsAfter() {
                return after;
            }

            @SuppressWarnings("unchecked")
            @Override
            public boolean processClass(ClassNode classNode, Type classType) {
                var targets = targetsByClassName.get(classType.getClassName());
                if (targets.isEmpty()) {
                    return false;
                }
                boolean transformed = false;
                switch (transformer.getTargetType()) {
                    case CLASS -> {
                        var context = new TransformationContext(classType.getClassName());
                        context.setNode(classNode);
                        transformer.transform((T) classNode, context);
                        transformed = true;
                    }
                    case METHOD -> {
                        var methodNameDescs = new HashSet<String>();
                        for (var target : targets) {
                            methodNameDescs.add(target.elementName() + target.elementDescriptor());
                        }
                        for (var method : classNode.methods) {
                            if (methodNameDescs.contains(method.name + method.desc)) {
                                var context = new TransformationContext(classType.getClassName());
                                context.setNode(method);
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
                        for (var field : classNode.fields) {
                            if (fieldNames.contains(field.name)) {
                                var context = new TransformationContext(classType.getClassName());
                                context.setNode(field);
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
