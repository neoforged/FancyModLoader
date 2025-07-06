package cpw.mods.modlauncher;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.transformation.IClassProcessor;
import net.neoforged.neoforgespi.transformation.IClassProcessorProvider;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class TransformStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final Map<String, IClassProcessor> transformers = new HashMap<>();
    private final List<IClassProcessor> sortedTransformers;
    
    @VisibleForTesting
    public TransformStore(ILaunchContext launchContext) {
        this.sortedTransformers = sortTransformers(
                launchContext,
                ServiceLoaderUtil.loadServices(launchContext, IClassProcessorProvider.class),
                ServiceLoaderUtil.loadServices(launchContext, IClassProcessor.class)
        );
    }
    
    @SuppressWarnings("UnstableApiUsage")
    private List<IClassProcessor> sortTransformers(ILaunchContext launchContext, List<IClassProcessorProvider> transformerProviders, List<IClassProcessor> existingTransformers) {
        final var graph = GraphBuilder.directed().<IClassProcessor>build();
        var specialComputeFramesNode = new IClassProcessor() {
            // This "special" transformer never handles a class but is always triggered

            @Override
            public String name() {
                return IClassProcessor.COMPUTING_FRAMES;
            }

            @Override
            public boolean handlesClass(Type classType, boolean isEmpty) {
                return false;
            }

            @Override
            public boolean processClass(ClassNode classNode, Type classType) {
                return false;
            }
        };
        graph.addNode(specialComputeFramesNode);
        transformers.put(specialComputeFramesNode.name(), specialComputeFramesNode);
        for (IClassProcessorProvider provider : transformerProviders) {
            for (var transformer : provider.makeTransformers(launchContext)) {
                addTransformer(transformer, graph);
            }
        }
        for (var transformer : existingTransformers) {
            addTransformer(transformer, graph);
        }
        for (var self : transformers.values()) {
            // If the targeted transformer is not present, then the ordering does not matter;
            // this allows for e.g. ordering with transformers that may or may not be present.
            for (var targetName : self.runsBefore()) {
                var target = transformers.get(targetName);
                if (target == self) {
                    continue;
                }
                if (target != null) {
                    graph.putEdge(self, target);
                }
            }
            for (var targetName : self.runsAfter()) {
                var target = transformers.get(targetName);
                if (target == self) {
                    continue;
                }
                if (target != null) {
                    graph.putEdge(target, self);
                }
            }
        }
        return TopologicalSort.topologicalSort(graph, Comparator.comparing(IClassProcessor::name));
    }

    @SuppressWarnings("UnstableApiUsage")
    private void addTransformer(IClassProcessor transformer, MutableGraph<IClassProcessor> graph) {
        if (transformers.containsKey(transformer.name())) {
            LOGGER.error(
                    "Duplicate transformers with name {}, of types {} and {}",
                    transformer.name(),
                    transformers.get(transformer.name()).getClass().getName(),
                    transformer.getClass().getName()
            );
            throw new IllegalStateException("Duplicate transformers with name: " + transformer.name());
        }
        graph.addNode(transformer);
        transformers.put(transformer.name(), transformer);
    }

    public void initializeBytecodeProvider(Function<String, IClassProcessor.IBytecodeProvider> function) {
        for (var transformer : sortedTransformers) {
            transformer.initializeBytecodeProvider(function.apply(transformer.name()));
        }
    }

    public List<IClassProcessor> transformersFor(Type classDesc, boolean isEmpty, String upToTransformer) {
        var out = new ArrayList<IClassProcessor>();
        boolean includesComputingFrames = false;
        for (var transformer : sortedTransformers) {
            if (upToTransformer != null && upToTransformer.equals(transformer.name())) {
                break;
            } else if (IClassProcessor.COMPUTING_FRAMES.equals(transformer.name())) {
                includesComputingFrames = true;
                out.add(transformer);
            } else if (transformer.handlesClass(classDesc, isEmpty)) {
                out.add(transformer);
            }
        }
        if (out.size() == 1 && includesComputingFrames) {
            // The class does not actually require any transformation, as the only transformer present is the special
            // no-op marker for where class hierarchy computation in frame computation goes up to.
            return List.of();
        }
        return out;
    }

    public Optional<IClassProcessor> findTransformer(String name) {
        return Optional.ofNullable(transformers.get(name));
    }
}
