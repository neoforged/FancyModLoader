package cpw.mods.modlauncher;

import com.google.common.graph.GraphBuilder;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.fml.loading.toposort.TopologicalSort;
import net.neoforged.neoforgespi.transformation.BytecodeProvider;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorFactory;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorMetadata;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

@ApiStatus.Internal
public class TransformStoreBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record AnnotatedFactory(ClassProcessorMetadata metadata, ClassProcessorFactory factory) {
        public AnnotatedFactory(ClassProcessor processor) {
            this(processor.metadata(), (metadataIgnored, providerIgnored) -> processor);
        }
    }

    private final List<AnnotatedFactory> processors = new ArrayList<>();
    private final Set<ProcessorName> markers = new HashSet<>();

    public void markMarker(ProcessorName name) {
        markers.add(name);
    }

    public void addProcessors(Collection<ClassProcessor> toAdd) {
        for (ClassProcessor classProcessor : toAdd) {
            processors.add(new AnnotatedFactory(classProcessor));
        }
    }

    public void addProcessorProviders(Collection<ClassProcessorProvider> providers) {
        for (var provider : providers) {
            provider.makeProcessors(new ClassProcessorProvider.ClassProcessorCollector() {
                @Override
                public void add(ClassProcessorMetadata metadata, ClassProcessorFactory factory) {
                    TransformStoreBuilder.this.processors.add(new AnnotatedFactory(metadata, factory));
                }

                @Override
                public void add(ClassProcessor processor) {
                    TransformStoreBuilder.this.processors.add(new AnnotatedFactory(processor));
                }
            });
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private static List<AnnotatedFactory> sortProcessors(List<AnnotatedFactory> allProcessors) {
        final var transformers = new HashMap<ProcessorName, AnnotatedFactory>();
        final var graph = GraphBuilder.directed().<AnnotatedFactory>build();

        var specialComputeFramesNode = createSpecialComputeFramesNode();

        graph.addNode(specialComputeFramesNode);
        transformers.put(specialComputeFramesNode.metadata.name(), specialComputeFramesNode);
        for (var transformer : allProcessors) {
            if (transformers.containsKey(transformer.metadata.name())) {
                LOGGER.error(
                        "Duplicate transformers with name {}, of types {} and {}",
                        transformer.metadata.name(),
                        transformers.get(transformer.metadata.name()).getClass().getName(),
                        transformer.getClass().getName());
                throw new IllegalStateException("Duplicate transformers with name: " + transformer.metadata.name());
            }
            graph.addNode(transformer);
            transformers.put(transformer.metadata.name(), transformer);
        }
        for (var self : transformers.values()) {
            // If the targeted transformer is not present, then the ordering does not matter;
            // this allows for e.g. ordering with transformers that may or may not be present.
            for (var targetName : self.metadata.runsBefore()) {
                var target = transformers.get(targetName);
                if (target == self) {
                    continue;
                }
                if (target != null) {
                    graph.putEdge(self, target);
                }
            }
            for (var targetName : self.metadata.runsAfter()) {
                var target = transformers.get(targetName);
                if (target == self) {
                    continue;
                }
                if (target != null) {
                    graph.putEdge(target, self);
                }
            }
        }
        return TopologicalSort.topologicalSort(graph, Comparator.<AnnotatedFactory, ClassProcessorMetadata.OrderingHint>comparing(t -> t.metadata.orderingHint()).thenComparing(TransformStoreBuilder::getNameSafe));
    }

    private static AnnotatedFactory createSpecialComputeFramesNode() {
        return new AnnotatedFactory(new ClassProcessorMetadata() {
            @Override
            public ProcessorName name() {
                return ClassProcessorIds.COMPUTING_FRAMES;
            }

            @Override
            public Set<ProcessorName> runsAfter() {
                return Set.of();
            }

            @Override
            public OrderingHint orderingHint() {
                return OrderingHint.EARLY;
            }
        }, (metadata, provider) -> new ClassProcessor() {
            @Override
            public ClassProcessorMetadata metadata() {
                return metadata;
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                return false;
            }

            @Override
            public ComputeFlags processClass(TransformationContext context) {
                return ComputeFlags.NO_REWRITE;
            }
        });
    }

    private static ProcessorName getNameSafe(AnnotatedFactory classProcessor) {
        var name = classProcessor.metadata.name();
        if (name == null) {
            throw new IllegalStateException("Class processor " + classProcessor + " has a null name");
        }
        return name;
    }

    public Set<String> getGeneratedPackages() {
        var out = new HashSet<String>();
        for (var factory : processors) {
            out.addAll(factory.metadata().generatesPackages());
        }
        return out;
    }

    public TransformStore build(Function<ProcessorName, BytecodeProvider> bytecodeProviders) {
        var sortedFactories = sortProcessors(processors);

        // We construct the processors and add them sequentially, to avoid needing lazy-initialization of some sort;
        // this way the TransformStore works in the meantime, which is necessary so that the bytecode provider provided
        // to the processors is "live" already when the processors are constructed/initialized. There's no way around
        // this, other than making the transform store a list of memoized behavior suppliers instead.
        var sortedProcessors = sortedFactories.stream()
                .map(factory -> {
                    var bytecodeProvider = bytecodeProviders.apply(factory.metadata().name());
                    return factory.factory.create(factory.metadata, bytecodeProvider);
                })
                .toList();

        return new TransformStore(sortedProcessors, markers);
    }
}
