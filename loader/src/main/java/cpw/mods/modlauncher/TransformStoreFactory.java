package cpw.mods.modlauncher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class TransformStoreFactory {
    private final List<TransformStoreBuilder.AnnotatedBehaviorFactory> factories;
    private final Set<ProcessorName> markerProcessors;

    TransformStoreFactory(List<TransformStoreBuilder.AnnotatedBehaviorFactory> factories, Set<ProcessorName> markerProcessors) {
        this.factories = factories;
        this.markerProcessors = markerProcessors;
    }

    public Set<ProcessorName> getMarkerProcessors() {
        return markerProcessors;
    }

    public Set<String> getGeneratedPackages() {
        var out = new HashSet<String>();
        for (var factory : factories) {
            out.addAll(factory.metadata().generatesPackages());
        }
        return out;
    }

    public void build(TransformStore store, Function<ProcessorName, ClassProcessor.BytecodeProvider> bytecodeProviders) {
        // We construct the processors and add them sequentially, to avoid needing lazy-initialization of some sort;
        // this way the TransformStore works in the meantime, which is necessary so that the bytecode provider provided
        // to the processors is "live" already when the processors are constructed/initialized. There's no way around
        // this, other than making the transform store a list of memoized behavior suppliers instead.
        for (var factory : factories) {
            var context = new ClassProcessor.InitializationContext(
                    bytecodeProviders.apply(factory.metadata().name()));
            var behavior = factory.behavior().apply(context);
            store.addProcessor(new TransformStore.AnnotatedBehavior(factory.metadata(), behavior));
        }
    }
}
