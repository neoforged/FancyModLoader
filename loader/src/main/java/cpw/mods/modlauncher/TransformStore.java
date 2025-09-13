/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

public class TransformStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ProcessorName, ClassProcessor> transformers = new HashMap<>();
    private final List<ClassProcessor> sortedTransformers;
    private final Set<String> generatedPackages = new HashSet<>();

    @VisibleForTesting
    public TransformStore(ILaunchContext launchContext) {
        this.sortedTransformers = sortTransformers(
                launchContext,
                ServiceLoaderUtil.loadServices(launchContext, ClassProcessorProvider.class),
                ServiceLoaderUtil.loadServices(launchContext, ClassProcessor.class));
    }

    @SuppressWarnings("UnstableApiUsage")
    private List<ClassProcessor> sortTransformers(ILaunchContext launchContext, List<ClassProcessorProvider> transformerProviders, List<ClassProcessor> existingTransformers) {
        final var graph = GraphBuilder.directed().<ClassProcessor>build();
        var specialComputeFramesNode = new ClassProcessor() {
            // This "special" transformer never handles a class but is always triggered
            @Override
            public ProcessorName name() {
                return ClassProcessor.COMPUTING_FRAMES;
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                return false;
            }

            @Override
            public Set<ProcessorName> runsAfter() {
                return Set.of();
            }

            @Override
            public boolean processClass(TransformationContext context) {
                return false;
            }
        };

        graph.addNode(specialComputeFramesNode);
        transformers.put(specialComputeFramesNode.name(), specialComputeFramesNode);
        for (ClassProcessorProvider provider : transformerProviders) {
            for (var transformer : provider.makeTransformers(launchContext)) {
                addTransformer(transformer, graph);
            }
        }
        for (var transformer : existingTransformers) {
            addTransformer(transformer, graph);
        }
        for (var self : transformers.values()) {
            this.generatedPackages.addAll(self.generatesPackages());
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
        return TopologicalSort.topologicalSort(graph, Comparator.comparing(ClassProcessor::name));
    }

    @SuppressWarnings("UnstableApiUsage")
    private void addTransformer(ClassProcessor transformer, MutableGraph<ClassProcessor> graph) {
        if (transformers.containsKey(transformer.name())) {
            LOGGER.error(
                    "Duplicate transformers with name {}, of types {} and {}",
                    transformer.name(),
                    transformers.get(transformer.name()).getClass().getName(),
                    transformer.getClass().getName());
            throw new IllegalStateException("Duplicate transformers with name: " + transformer.name());
        }
        graph.addNode(transformer);
        transformers.put(transformer.name(), transformer);
    }

    public void initializeBytecodeProvider(Function<ProcessorName, ClassProcessor.BytecodeProvider> function, IEnvironment environment) {
        for (var transformer : sortedTransformers) {
            transformer.initializeBytecodeProvider(function.apply(transformer.name()), environment);
        }
    }

    public List<ClassProcessor> transformersFor(Type classDesc, boolean isEmpty, ProcessorName upToTransformer) {
        var out = new ArrayList<ClassProcessor>();
        boolean includesComputingFrames = false;
        for (var transformer : sortedTransformers) {
            if (upToTransformer != null && upToTransformer.equals(transformer.name())) {
                break;
            } else if (ClassProcessor.COMPUTING_FRAMES.equals(transformer.name())) {
                includesComputingFrames = true;
                out.add(transformer);
            } else {
                ClassTransformStatistics.incrementAskedForTransform(transformer);

                var context = new ClassProcessor.SelectionContext(classDesc, isEmpty);
                if (transformer.handlesClass(context)) {
                    ClassTransformStatistics.incrementTransforms(transformer);
                    out.add(transformer);
                }
            }
        }
        if ((out.size() == 1 && includesComputingFrames)) {
            // The class does not actually require any transformation, as the only transformer present is the special
            // no-op marker for where class hierarchy computation in frame computation goes up to, and potentially the
            // marker for where results are fixed and may be responded to.
            return List.of();
        }
        return out;
    }

    public Set<String> generatedPackages() {
        return Collections.unmodifiableSet(generatedPackages);
    }

    public Optional<ClassProcessor> findClassProcessor(ProcessorName name) {
        return Optional.ofNullable(transformers.get(name));
    }
}
