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
import com.mojang.logging.LogUtils;
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
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

@ApiStatus.Internal
public class TransformStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ProcessorName, ClassProcessor> transformers = new HashMap<>();
    private final List<ClassProcessor> sortedTransformers;
    private final Set<String> generatedPackages = new HashSet<>();
    private final Set<ProcessorName> markerProcessors = new HashSet<>();

    @VisibleForTesting
    public TransformStore(List<ClassProcessor> processors) {
        this(processors, Map.of(), Set.of());
    }

    TransformStore(List<ClassProcessor> processors, Map<ProcessorName, BytecodeProviderImpl> bytecodeProviders, Set<ProcessorName> markers) {
        this.sortedTransformers = sortTransformers(processors);
        this.bytecodeProviders.putAll(bytecodeProviders);
        CrashReportCallables.registerCrashCallable("Class Processors", () -> ClassTransformStatistics.computeCrashReportEntry(this));
        this.markerProcessors.addAll(markers);
    }

    public boolean isMarker(ClassProcessor processor) {
        return markerProcessors.contains(processor.name());
    }

    @VisibleForTesting
    public List<ClassProcessor> getSortedTransformers() {
        return sortedTransformers;
    }

    @SuppressWarnings("UnstableApiUsage")
    private List<ClassProcessor> sortTransformers(List<ClassProcessor> allTransformers) {
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
            public ComputeFlags processClass(TransformationContext context) {
                return ComputeFlags.COMPUTE_FRAMES;
            }
        };

        graph.addNode(specialComputeFramesNode);
        transformers.put(specialComputeFramesNode.name(), specialComputeFramesNode);
        for (var transformer : allTransformers) {
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
        return TopologicalSort.topologicalSort(graph, Comparator.comparing(TransformStore::getNameSafe));
    }

    private static ProcessorName getNameSafe(ClassProcessor classProcessor) {
        var name = classProcessor.name();
        if (name == null) {
            throw new IllegalStateException("Class processor " + classProcessor.getClass().getName() + " returns a null name");
        }
        return name;
    }

    private final Map<ProcessorName, BytecodeProviderImpl> bytecodeProviders = new HashMap<>();

    static final class BytecodeProviderImpl implements ClassProcessor.BytecodeProvider {
        private ClassProcessor.BytecodeProvider provider;

        @Override
        public byte[] acquireTransformedClassBefore(String className) throws ClassNotFoundException {
            if (provider == null) {
                throw new IllegalStateException("Bytecode provider not yet available");
            }
            return provider.acquireTransformedClassBefore(className);
        }
    }

    public void linkBytecodeProviders(Function<ProcessorName, ClassProcessor.BytecodeProvider> function) {
        for (var transformer : sortedTransformers) {
            var provider = function.apply(transformer.name());
            var impl = bytecodeProviders.get(transformer.name());
            if (provider != null && impl != null) {
                impl.provider = provider;
            }
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

    Optional<ClassProcessor> findClassProcessor(ProcessorName name) {
        return Optional.ofNullable(transformers.get(name));
    }
}
