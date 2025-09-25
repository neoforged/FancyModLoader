package cpw.mods.modlauncher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorBehavior;
import net.neoforged.neoforgespi.transformation.ClassProcessorMetadata;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class TransformStoreBuilder {
    private final List<ClassProcessor> processors = new ArrayList<>();
    private final Set<ProcessorName> markers = new HashSet<>();

    public void markMarker(ProcessorName name) {
        markers.add(name);
    }

    public void addProcessors(Collection<ClassProcessor> toAdd) {
        this.processors.addAll(toAdd);
    }

    public void addProcessorProviders(Collection<ClassProcessorProvider> providers) {
        for (var provider : providers) {
            provider.makeProcessors(new ClassProcessorProvider.ClassProcessorCollector() {
                @Override
                public void add(ClassProcessorMetadata metadata, Function<ClassProcessor.InitializationContext, ClassProcessorBehavior> factory) {
                    TransformStoreBuilder.this.processors.add(new ClassProcessor() {
                        @Override
                        public Set<ProcessorName> runsBefore() {
                            return metadata.runsBefore();
                        }

                        @Override
                        public Set<ProcessorName> runsAfter() {
                            return metadata.runsAfter();
                        }

                        @Override
                        public ProcessorName name() {
                            return metadata.name();
                        }

                        @Override
                        public Set<String> generatesPackages() {
                            return metadata.generatesPackages();
                        }

                        private ClassProcessorBehavior behaviour;
                        private InitializationContext initContext;

                        @Override
                        public boolean handlesClass(SelectionContext context) {
                            initializeBehavior();
                            return behaviour.handlesClass(context);
                        }

                        @Override
                        public ComputeFlags processClass(TransformationContext context) {
                            initializeBehavior();
                            return behaviour.processClass(context);
                        }

                        @Override
                        public void afterProcessing(AfterProcessingContext context) {
                            initializeBehavior();
                            behaviour.afterProcessing(context);
                        }

                        @Override
                        public void initialize(InitializationContext context) {
                            this.initContext = context;
                        }

                        private synchronized void initializeBehavior() {
                            if (behaviour == null && initContext != null) {
                                var behaviour = factory.apply(this.initContext);
                                if (behaviour == null) {
                                    throw new IllegalStateException("Processor factory attempted to register null processor for name " + metadata);
                                }
                                this.behaviour = behaviour;
                                this.initContext = null;
                            }
                        }
                    });
                }

                @Override
                public void add(ClassProcessor processor) {
                    if (processor == null) {
                        throw new IllegalStateException("Processor collector attempted to register null processor");
                    }
                    TransformStoreBuilder.this.processors.add(processor);
                }
            });
        }
    }

    public TransformStore build() {
        return new TransformStore(this.processors, this.markers);
    }
}
