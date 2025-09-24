package cpw.mods.modlauncher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final Map<ProcessorName, TransformStore.BytecodeProviderImpl> bytecodeProviders = new HashMap<>();
    private final ClassProcessorLocatorImpl locator = new ClassProcessorLocatorImpl();

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

    private ClassProcessor.InitializationContext contextFor(ProcessorName name) {
        var bytecodeProvider = bytecodeProviders.computeIfAbsent(name, k -> new TransformStore.BytecodeProviderImpl());
        return new ClassProcessor.InitializationContext(bytecodeProvider, locator);
    }

    public TransformStore build() {
        for (var processor : this.processors) {
            var context = contextFor(processor.name());
            processor.initialize(context);
        }
        var store = new TransformStore(this.processors, this.bytecodeProviders, this.markers);
        this.locator.delegate = store::findClassProcessor;
        return store;
    }

    private static final class ClassProcessorLocatorImpl implements ClassProcessor.ClassProcessorLocator {
        private ClassProcessor.ClassProcessorLocator delegate;

        @Override
        public Optional<ClassProcessor> find(ProcessorName name) {
            if (delegate == null) {
                throw new IllegalStateException("Locator not yet initialized");
            }
            return delegate.find(name);
        }
    }
}
