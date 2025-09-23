package cpw.mods.modlauncher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class TransformStoreBuilder {
    private final ILaunchContext context;
    private final List<ClassProcessor> processors = new ArrayList<>();
    private final Set<ProcessorName> markers = new HashSet<>();

    private final Map<ProcessorName, TransformStore.BytecodeProviderImpl> bytecodeProviders = new HashMap<>();
    private final ClassProcessorLocatorImpl locator = new ClassProcessorLocatorImpl();

    public TransformStoreBuilder(ILaunchContext context) {
        this.context = context;
    }

    public void markMarker(ProcessorName name) {
        markers.add(name);
    }

    public void addProcessors(Collection<ClassProcessor> toAdd) {
        this.processors.addAll(toAdd);
    }

    public void addProcessorProviders(Collection<ClassProcessorProvider> providers) {
        for (var provider : providers) {
            provider.makeProcessors((name, factory) -> {
                var context = contextFor(name);
                var processor = factory.apply(context);
                if (processor == null) {
                    throw new IllegalStateException("Processor factory attempted to register null processor for name " + name);
                } else if (!processor.name().equals(name)) {
                    throw new IllegalStateException("Processor factory for name " + name + " returned processor with name " + processor.name());
                }
                this.processors.add(processor);
            }, this.context);
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
