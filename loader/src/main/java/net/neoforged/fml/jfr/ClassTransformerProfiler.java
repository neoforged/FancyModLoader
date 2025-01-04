package net.neoforged.fml.jfr;

import cpw.mods.modlauncher.ClassTransformer;
import jdk.jfr.FlightRecorder;

public class ClassTransformerProfiler implements AutoCloseable {
    private final ClassTransformer transformer;
    private final Runnable emitter;

    public ClassTransformerProfiler(ClassTransformer transformer) {
        this.transformer = transformer;
        this.emitter = this::emit;
        FlightRecorder.addPeriodicEvent(ClassTransformerStatistics.class, emitter);
    }

    private void emit() {
        var event = new ClassTransformerStatistics(
                transformer.getTransformedClasses(),
                transformer.getClassParsingTime(),
                transformer.getClassTransformingTime());
        event.commit();
    }

    @Override
    public void close() throws Exception {
        FlightRecorder.removePeriodicEvent(emitter);
    }
}
